import { errorMessage, outcomeAfterFailure } from './retryPolicy.js';
import type { ConsumerRegistry } from './subscriptions.js';
import type { ClaimFilter, Delivery, EventQueue } from './types.js';

export interface DispatcherOptions {
  /** Entregas que se reclaman por tanda. */
  batchSize?: number;
  /** Cuántas entregas de una tanda se ejecutan a la vez (limita la presión sobre tiendas y servicios externos). */
  concurrency?: number;
  /** Tiempo total máximo de una corrida; debe quedar por debajo del `maxDuration` de la función (60 s). */
  budgetMs?: number;
  /** Arrendamiento de una entrega reclamada: si la función muere, otra la recupera al vencer. */
  lockSeconds?: number;
  /** Tope de un consumidor por entrega; pasado ese tiempo se cuenta como fallo transitorio. */
  handlerTimeoutMs?: number;
  now?: () => number;
  random?: () => number;
  log?: (line: string) => void;
}

/** Acota una corrida: solo las entregas de una petición y/o de ciertos consumidores, con su propio presupuesto. */
export interface RunOptions extends ClaimFilter {
  budgetMs?: number;
}

export interface DispatchSummary {
  reclamadas: number;
  hechas: number;
  reintentos: number;
  muertas: number;
  /** Entregas cuyo resultado no se pudo registrar (p. ej. la base se cayó): se recuperarán por arrendamiento. */
  sinRegistrar: number;
  /** Entregas terminadas con éxito, por consumidor. */
  hechasPorConsumidor: Record<string, number>;
  duracionMs: number;
}

class HandlerTimeoutError extends Error {
  constructor(consumer: string, ms: number) {
    super(`El consumidor ${consumer} superó ${ms} ms`);
    this.name = 'HandlerTimeoutError';
  }
}

/**
 * Reclama entregas, invoca al consumidor de cada una y registra el resultado. No contiene lógica de
 * negocio: enruta, reintenta (backoff), manda a la cola de fallos y registra. Cada consumidor y
 * cada evento tienen estado propio, así que una entrega que falla no bloquea a las demás.
 */
export class EventDispatcher {
  private readonly batchSize: number;
  private readonly concurrency: number;
  private readonly budgetMs: number;
  private readonly lockSeconds: number;
  private readonly handlerTimeoutMs: number;
  private readonly now: () => number;
  private readonly random: () => number;
  private readonly log: (line: string) => void;

  constructor(
    private readonly queue: EventQueue,
    private readonly registry: ConsumerRegistry,
    options: DispatcherOptions = {}
  ) {
    this.batchSize = options.batchSize ?? 20;
    this.concurrency = options.concurrency ?? 8;
    this.budgetMs = options.budgetMs ?? 45_000;
    // Más que el presupuesto de la corrida: una entrega reclamada no se recupera mientras esta siga viva.
    this.lockSeconds = options.lockSeconds ?? 120;
    this.handlerTimeoutMs = options.handlerTimeoutMs ?? 20_000;
    this.now = options.now ?? Date.now;
    this.random = options.random ?? Math.random;
    this.log = options.log ?? ((line) => console.log(line));
  }

  async run(options: RunOptions = {}): Promise<DispatchSummary> {
    const started = this.now();
    const budget = options.budgetMs ?? this.budgetMs;
    // Con un presupuesto corto (despacho en línea) el tope por entrega se acorta también.
    const timeout = Math.min(this.handlerTimeoutMs, budget);
    // Solo se reclaman entregas de consumidores que ESTE despachador conoce. Si otro código comparte la base
    // (una vista previa de Vercel, un despliegue a medias, un servidor local), no puede gastar los
    // intentos de entregas que no sabe procesar: quedan pendientes para quien sí las conoce.
    const known = this.registry.all().map((c) => c.name);
    const consumers = options.consumers ? options.consumers.filter((name) => known.includes(name)) : known;
    const filter: ClaimFilter = { correlationId: options.correlationId, consumers };
    const summary: DispatchSummary = { reclamadas: 0, hechas: 0, reintentos: 0, muertas: 0, sinRegistrar: 0, hechasPorConsumidor: {}, duracionMs: 0 };
    if (consumers.length === 0) {
      return summary; // una lista vacía significaría «sin filtro» para la cola: aquí quiere decir «nadie que atender»
    }

    // Se deja de reclamar cuando ya no cabría otra entrega completa dentro del presupuesto.
    let first = true;
    while (first || this.now() - started < budget - timeout) {
      first = false;
      const batch = await this.queue.claim(this.batchSize, this.lockSeconds, filter);
      if (batch.length === 0) {
        break;
      }
      summary.reclamadas += batch.length;
      // Una tanda nunca trae dos entregas del mismo consumidor y agregado (lo garantiza el reclamo),
      // así que se pueden ejecutar en paralelo sin romper el orden.
      const pending = [...batch];
      const workers = Array.from({ length: Math.min(this.concurrency, pending.length) }, async () => {
        for (let next = pending.shift(); next; next = pending.shift()) {
          await this.deliver(next, summary, timeout);
        }
      });
      await Promise.all(workers);
    }
    summary.duracionMs = this.now() - started;
    return summary;
  }

  private async deliver(delivery: Delivery, summary: DispatchSummary, timeoutMs: number): Promise<void> {
    const started = this.now();
    const { consumer: name, eventId, attempts, event } = delivery;
    const base = { componente: 'events.dispatcher', eventId, tipo: event.type, consumidor: name, correlationId: event.correlationId, intento: attempts };
    try {
      const consumer = this.registry.get(name);
      if (!consumer) {
        // Entrega de un consumidor que esta versión del código no conoce (despliegue desfasado): se reintenta.
        throw new Error(`Consumidor no registrado: ${name}`);
      }
      await this.withTimeout(consumer.handle(event), name, timeoutMs);
      await this.record(() => this.queue.ack(eventId, name), summary);
      summary.hechas += 1;
      summary.hechasPorConsumidor[name] = (summary.hechasPorConsumidor[name] ?? 0) + 1;
      this.write({ ...base, nivel: 'info', resultado: 'ok', duracionMs: this.now() - started });
    } catch (err) {
      const outcome = outcomeAfterFailure(attempts, err, this.random);
      const error = errorMessage(err);
      if (outcome.status === 'dead') {
        await this.record(() => this.queue.kill(eventId, name, error), summary);
        summary.muertas += 1;
        this.write({ ...base, nivel: 'error', resultado: 'muerto', error, duracionMs: this.now() - started });
      } else {
        await this.record(() => this.queue.fail(eventId, name, error, outcome.retryInSeconds), summary);
        summary.reintentos += 1;
        this.write({ ...base, nivel: 'warn', resultado: 'reintento', reintentoEnS: outcome.retryInSeconds, error, duracionMs: this.now() - started });
      }
    }
  }

  /** Registrar el resultado también puede fallar (la base cayó): no se propaga; el arrendamiento la recupera. */
  private async record(action: () => Promise<void>, summary: DispatchSummary): Promise<void> {
    try {
      await action();
    } catch (err) {
      summary.sinRegistrar += 1;
      this.write({ componente: 'events.dispatcher', nivel: 'error', resultado: 'sin_registrar', error: errorMessage(err) });
    }
  }

  private withTimeout(work: Promise<void>, consumer: string, timeoutMs: number): Promise<void> {
    let timer: ReturnType<typeof setTimeout> | undefined;
    const timeout = new Promise<never>((_, reject) => {
      timer = setTimeout(() => reject(new HandlerTimeoutError(consumer, timeoutMs)), timeoutMs);
    });
    return Promise.race([work, timeout]).finally(() => clearTimeout(timer));
  }

  private write(entry: Record<string, unknown>): void {
    this.log(JSON.stringify({ ts: new Date(this.now()).toISOString(), ...entry }));
  }
}
