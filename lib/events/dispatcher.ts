import { errorMessage, outcomeAfterFailure } from './retryPolicy.js';
import type { ConsumerRegistry } from './subscriptions.js';
import type { Delivery, EventQueue } from './types.js';

export interface DispatcherOptions {
  /** Entregas que se reclaman por tanda. */
  batchSize?: number;
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

export interface DispatchSummary {
  reclamadas: number;
  hechas: number;
  reintentos: number;
  muertas: number;
  /** Entregas cuyo resultado no se pudo registrar (p. ej. la base se cayó): se recuperarán por arrendamiento. */
  sinRegistrar: number;
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
    this.budgetMs = options.budgetMs ?? 45_000;
    this.lockSeconds = options.lockSeconds ?? 60;
    this.handlerTimeoutMs = options.handlerTimeoutMs ?? 20_000;
    this.now = options.now ?? Date.now;
    this.random = options.random ?? Math.random;
    this.log = options.log ?? ((line) => console.log(line));
  }

  async run(): Promise<DispatchSummary> {
    const started = this.now();
    const summary: DispatchSummary = { reclamadas: 0, hechas: 0, reintentos: 0, muertas: 0, sinRegistrar: 0, duracionMs: 0 };
    // Se deja de reclamar cuando ya no cabría otra entrega completa dentro del presupuesto.
    while (this.now() - started < this.budgetMs - this.handlerTimeoutMs) {
      const batch = await this.queue.claim(this.batchSize, this.lockSeconds);
      if (batch.length === 0) {
        break;
      }
      summary.reclamadas += batch.length;
      // Una tanda nunca trae dos entregas del mismo consumidor y agregado (lo garantiza el reclamo),
      // así que se pueden ejecutar en paralelo sin romper el orden.
      await Promise.all(batch.map((delivery) => this.deliver(delivery, summary)));
    }
    summary.duracionMs = this.now() - started;
    return summary;
  }

  private async deliver(delivery: Delivery, summary: DispatchSummary): Promise<void> {
    const started = this.now();
    const { consumer: name, eventId, attempts, event } = delivery;
    const base = { componente: 'events.dispatcher', eventId, tipo: event.type, consumidor: name, correlationId: event.correlationId, intento: attempts };
    try {
      const consumer = this.registry.get(name);
      if (!consumer) {
        // Entrega de un consumidor que esta versión del código no conoce (despliegue desfasado): se reintenta.
        throw new Error(`Consumidor no registrado: ${name}`);
      }
      await this.withTimeout(consumer.handle(event), name);
      await this.record(() => this.queue.ack(eventId, name), summary);
      summary.hechas += 1;
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

  private withTimeout(work: Promise<void>, consumer: string): Promise<void> {
    let timer: ReturnType<typeof setTimeout> | undefined;
    const timeout = new Promise<never>((_, reject) => {
      timer = setTimeout(() => reject(new HandlerTimeoutError(consumer, this.handlerTimeoutMs)), this.handlerTimeoutMs);
    });
    return Promise.race([work, timeout]).finally(() => clearTimeout(timer));
  }

  private write(entry: Record<string, unknown>): void {
    this.log(JSON.stringify({ ts: new Date(this.now()).toISOString(), ...entry }));
  }
}
