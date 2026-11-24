import type { EventPayloads, EventType } from './catalog.js';

/** Lo que un productor entrega al bus. El id, la fecha y las entregas las asigna el bus. */
export interface PublishInput<T extends EventType = EventType> {
  type: T;
  aggregateType: string;
  aggregateId: string;
  payload: EventPayloads[T];
  /** Id de la petición HTTP (o corrida) que originó el evento: une toda la cadena de reacciones. */
  correlationId?: string | null;
  /** Evento que causó a este (cadena de reacciones). */
  causationId?: number | null;
  /** `user:<id>` o `system`. */
  actor?: string;
  /** Si ya existe un evento con la misma clave, no se publica otro (idempotencia del productor). */
  dedupeKey?: string | null;
}

export interface StoredEvent {
  id: number;
  type: string;
  version: number;
  aggregateType: string;
  aggregateId: string;
  payload: unknown;
  occurredAt: Date;
  correlationId: string | null;
  causationId: number | null;
  actor: string;
}

/** Una entrega reclamada: este consumidor debe procesar este evento (intento `attempts`). */
export interface Delivery {
  eventId: number;
  consumer: string;
  attempts: number;
  event: StoredEvent;
}

/**
 * Un consumidor reacciona a los tipos de evento que declara. Debe ser idempotente: la entrega es
 * al-menos-una-vez, así que el mismo evento puede llegar dos veces y el resultado debe ser el mismo.
 */
export interface Consumer {
  /** Identificador estable (minúsculas y guion bajo): es la clave de su estado en `event_deliveries`. */
  readonly name: string;
  readonly handles: readonly EventType[];
  handle(event: StoredEvent): Promise<void>;
}

export type DeliveryStatus = 'pending' | 'processing' | 'done' | 'failed' | 'dead';

export interface ConsumerStats {
  pending: number;
  processing: number;
  done: number;
  failed: number;
  dead: number;
}

export interface EventStats {
  /** Segundos desde el evento sin entregar más antiguo; 0 si no hay nada pendiente. */
  lagSegundos: number;
  muertos: number;
  porConsumidor: Record<string, ConsumerStats>;
}

/** Acota qué entregas se reclaman (por ejemplo, solo las de una petición y solo ciertos consumidores). */
export interface ClaimFilter {
  correlationId?: string;
  consumers?: readonly string[];
}

/** Cola de entregas. La implementa Postgres en producción y una versión en memoria en las pruebas. */
export interface EventQueue {
  /** Reclama hasta `limit` entregas listas y las marca en curso por `lockSeconds`. */
  claim(limit: number, lockSeconds: number, filter?: ClaimFilter): Promise<Delivery[]>;
  ack(eventId: number, consumer: string): Promise<void>;
  /** Falló, pero se puede reintentar dentro de `retryInSeconds`. */
  fail(eventId: number, consumer: string, error: string, retryInSeconds: number): Promise<void>;
  /** Falló sin remedio (o agotó los intentos): pasa a la cola de fallos. */
  kill(eventId: number, consumer: string, error: string): Promise<void>;
  /** Devuelve a «pendiente» las entregas muertas (de un evento y/o consumidor); cuenta cuántas repuso. */
  requeue(filter: { eventId?: number; consumer?: string }): Promise<number>;
  stats(): Promise<EventStats>;
}
