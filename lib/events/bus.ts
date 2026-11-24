import type { TransactionSql } from 'postgres';
import type { Sql } from '../db/client.js';
import { EVENT_VERSION, validatePayload, type EventType } from './catalog.js';
import type { PublishInput } from './types.js';

type Queryable = Sql | TransactionSql;

/**
 * Bus de eventos con el patrón outbox: el evento se publica DENTRO de la transacción que hace el
 * cambio. Si esa transacción se revierte, el evento tampoco existe; si se confirma, el evento ya
 * está guardado y un despachador lo entregará después. Nunca hay un cambio sin su evento ni un
 * evento sin su cambio.
 */
export interface EventBus {
  /** Devuelve el id del evento, o null si `dedupeKey` ya existía (no se publicó otro). */
  publish<T extends EventType>(tx: Queryable, input: PublishInput<T>): Promise<number | null>;
}

/**
 * EVENTS_DISPATCH=on activa el envelope completo y las entregas por consumidor (requiere la
 * migración 006). Apagado (por defecto) se comporta como antes: solo se guarda el evento en la
 * bandeja, con las cuatro columnas originales, y funciona aunque la 006 no esté aplicada.
 */
export const isDispatchEnabled = (): boolean => process.env.EVENTS_DISPATCH === 'on';

/** Los únicos tipos que se publicaban antes del bus: con el despacho apagado, los demás no se escriben. */
const LEGACY_TYPES: ReadonlySet<EventType> = new Set<EventType>(['watch.checked', 'alert.created']);

export class PgEventBus implements EventBus {
  constructor(
    /** Nombres de los consumidores suscritos a un tipo: una fila de entrega por cada uno. */
    private readonly consumersFor: (type: EventType) => readonly string[] = () => [],
    private readonly enabled: () => boolean = isDispatchEnabled
  ) {}

  async publish<T extends EventType>(tx: Queryable, input: PublishInput<T>): Promise<number | null> {
    // Se valida al publicar: un payload mal formado es un bug del productor y debe verse ahí.
    const payload = validatePayload(input.type, input.payload);

    if (!this.enabled()) {
      // Sin despacho, un tipo nuevo no tendría quién lo lea: no se guarda (producción sigue igual que antes).
      if (!LEGACY_TYPES.has(input.type)) {
        return null;
      }
      const [legacy] = await tx<{ id: string }[]>`
        insert into public.domain_events (aggregate_type, aggregate_id, event_type, payload)
        values (${input.aggregateType}, ${input.aggregateId}, ${input.type}, ${tx.json(payload)})
        returning id::text as id`;
      return Number(legacy.id);
    }

    const [row] = await tx<{ id: string }[]>`
      insert into public.domain_events
        (aggregate_type, aggregate_id, event_type, payload, version, correlation_id, causation_id, actor, dedupe_key)
      values
        (${input.aggregateType}, ${input.aggregateId}, ${input.type}, ${tx.json(payload)}, ${EVENT_VERSION},
         ${input.correlationId ?? null}, ${input.causationId ?? null}, ${input.actor ?? 'system'}, ${input.dedupeKey ?? null})
      on conflict (dedupe_key) where dedupe_key is not null do nothing
      returning id::text as id`;
    if (!row) {
      return null;
    }
    const eventId = Number(row.id);
    // Fan-out al escribir: cada consumidor suscrito tiene su propia fila (estado, intentos, cola de fallos).
    for (const consumer of this.consumersFor(input.type)) {
      await tx`insert into public.event_deliveries (event_id, consumer) values (${eventId}, ${consumer})`;
    }
    return eventId;
  }
}
