// Versiones en memoria del bus y de la cola, con la MISMA semántica que las de Postgres, para probar
// productores, consumidores y despachador sin base de datos (igual que los repositorios en memoria
// de las pruebas de los servicios). Las de Postgres se prueban aparte, contra una base real.
import { EVENT_VERSION, validatePayload, type EventType } from './catalog.js';
import type { EventBus } from './bus.js';
import type { ConsumerStats, Delivery, DeliveryStatus, EventQueue, EventStats, PublishInput, StoredEvent } from './types.js';

export class InMemoryEventBus implements EventBus {
  readonly published: Array<{ id: number; input: PublishInput }> = [];
  private readonly dedupe = new Set<string>();

  constructor(private readonly onPublish?: (event: StoredEvent, input: PublishInput) => void) {}

  async publish<T extends EventType>(_tx: unknown, input: PublishInput<T>): Promise<number | null> {
    const payload = validatePayload(input.type, input.payload);
    if (input.dedupeKey) {
      if (this.dedupe.has(input.dedupeKey)) return null;
      this.dedupe.add(input.dedupeKey);
    }
    const id = this.published.length + 1;
    this.published.push({ id, input: { ...input, payload } as PublishInput });
    this.onPublish?.(
      {
        id,
        type: input.type,
        version: EVENT_VERSION,
        aggregateType: input.aggregateType,
        aggregateId: input.aggregateId,
        payload,
        occurredAt: new Date(),
        correlationId: input.correlationId ?? null,
        causationId: input.causationId ?? null,
        actor: input.actor ?? 'system',
      },
      input as PublishInput
    );
    return id;
  }

  ofType(type: EventType) {
    return this.published.filter((p) => p.input.type === type);
  }
}

interface Row {
  event: StoredEvent;
  consumer: string;
  status: DeliveryStatus;
  attempts: number;
  nextAttemptAt: number;
  lockedUntil: number | null;
  lastError: string | null;
}

export class InMemoryEventQueue implements EventQueue {
  readonly rows: Row[] = [];

  constructor(private readonly now: () => number = Date.now) {}

  /** Equivale a publicar un evento con sus consumidores suscritos. */
  enqueue(event: StoredEvent, consumers: readonly string[]): void {
    for (const consumer of consumers) {
      this.rows.push({ event, consumer, status: 'pending', attempts: 0, nextAttemptAt: this.now(), lockedUntil: null, lastError: null });
    }
  }

  row(eventId: number, consumer: string): Row {
    const found = this.rows.find((r) => r.event.id === eventId && r.consumer === consumer);
    if (!found) throw new Error(`No hay entrega ${eventId}/${consumer}`);
    return found;
  }

  async claim(limit: number, lockSeconds: number): Promise<Delivery[]> {
    const t = this.now();
    const ready = (r: Row) =>
      ((r.status === 'pending' || r.status === 'failed') && r.nextAttemptAt <= t) || (r.status === 'processing' && r.lockedUntil !== null && r.lockedUntil < t);
    // Mismo orden por agregado que el SQL: un evento anterior sin terminar (que no esté muerto) bloquea a los siguientes.
    const blocked = (r: Row) =>
      this.rows.some(
        (o) =>
          o.consumer === r.consumer &&
          o.event.id < r.event.id &&
          o.status !== 'done' &&
          o.status !== 'dead' &&
          o.event.aggregateType === r.event.aggregateType &&
          o.event.aggregateId === r.event.aggregateId
      );
    const picked = this.rows
      .filter((r) => ready(r) && !blocked(r))
      .sort((a, b) => a.event.id - b.event.id || a.consumer.localeCompare(b.consumer))
      .slice(0, limit);
    for (const r of picked) {
      r.status = 'processing';
      r.attempts += 1;
      r.lockedUntil = t + lockSeconds * 1000;
    }
    return picked.map((r) => ({ eventId: r.event.id, consumer: r.consumer, attempts: r.attempts, event: r.event }));
  }

  async ack(eventId: number, consumer: string): Promise<void> {
    Object.assign(this.row(eventId, consumer), { status: 'done', lockedUntil: null, lastError: null });
  }

  async fail(eventId: number, consumer: string, error: string, retryInSeconds: number): Promise<void> {
    Object.assign(this.row(eventId, consumer), { status: 'failed', lastError: error, lockedUntil: null, nextAttemptAt: this.now() + retryInSeconds * 1000 });
  }

  async kill(eventId: number, consumer: string, error: string): Promise<void> {
    Object.assign(this.row(eventId, consumer), { status: 'dead', lastError: error, lockedUntil: null });
  }

  async requeue(filter: { eventId?: number; consumer?: string }): Promise<number> {
    const dead = this.rows.filter(
      (r) => r.status === 'dead' && (filter.eventId === undefined || r.event.id === filter.eventId) && (filter.consumer === undefined || r.consumer === filter.consumer)
    );
    for (const r of dead) Object.assign(r, { status: 'pending', attempts: 0, nextAttemptAt: this.now(), lockedUntil: null, lastError: null });
    return dead.length;
  }

  async stats(): Promise<EventStats> {
    const porConsumidor: Record<string, ConsumerStats> = {};
    let muertos = 0;
    for (const r of this.rows) {
      porConsumidor[r.consumer] ??= { pending: 0, processing: 0, done: 0, failed: 0, dead: 0 };
      porConsumidor[r.consumer][r.status] += 1;
      if (r.status === 'dead') muertos += 1;
    }
    const open = this.rows.filter((r) => r.status === 'pending' || r.status === 'processing' || r.status === 'failed');
    const oldest = open.length ? Math.min(...open.map((r) => r.event.occurredAt.getTime())) : null;
    return { lagSegundos: oldest === null ? 0 : Math.max(0, Math.floor((this.now() - oldest) / 1000)), muertos, porConsumidor };
  }
}
