import type { Sql } from '../db/client.js';
import type { ConsumerStats, Delivery, EventQueue, EventStats, StoredEvent } from './types.js';

interface EventRow {
  id: string;
  event_type: string;
  version: number;
  aggregate_type: string;
  aggregate_id: string;
  payload: unknown;
  occurred_at: Date;
  correlation_id: string | null;
  causation_id: string | null;
  actor: string;
}

const emptyStats = (): ConsumerStats => ({ pending: 0, processing: 0, done: 0, failed: 0, dead: 0 });

/** Cola de entregas sobre Postgres: lo único que sabe de SQL en el módulo de eventos. */
export class PgEventQueue implements EventQueue {
  constructor(private readonly sql: Sql) {}

  async claim(limit: number, lockSeconds: number): Promise<Delivery[]> {
    const { sql } = this;
    // Una sola sentencia: elige con `for update skip locked` (dos despachadores simultáneos nunca
    // reciben la misma entrega) y la marca «en curso» con un arrendamiento. Si la función muere,
    // el arrendamiento vence y la entrega vuelve a estar disponible.
    // Orden por agregado: no se entrega un evento mientras haya otro anterior, del mismo agregado y
    // para el mismo consumidor, sin terminar (los muertos no bloquean). Ver nota en el plan: entre
    // dos despachadores exactamente simultáneos puede haber una ventana mínima; los consumidores
    // son idempotentes, así que el orden estricto no es un requisito de corrección.
    const claimed = await sql<{ event_id: string; consumer: string; attempts: number }[]>`
      with picked as (
        select d.event_id, d.consumer
        from public.event_deliveries d
        join public.domain_events e on e.id = d.event_id
        where ((d.status in ('pending', 'failed') and d.next_attempt_at <= now())
            or (d.status = 'processing' and d.locked_until < now()))
          and not exists (
            select 1
            from public.event_deliveries d2
            join public.domain_events e2 on e2.id = d2.event_id
            where d2.consumer = d.consumer
              and d2.event_id < d.event_id
              and d2.status not in ('done', 'dead')
              and e2.aggregate_type = e.aggregate_type
              and e2.aggregate_id = e.aggregate_id)
        order by d.event_id, d.consumer
        for update of d skip locked
        limit ${limit})
      update public.event_deliveries d
         set status = 'processing',
             attempts = d.attempts + 1,
             locked_until = now() + make_interval(secs => ${lockSeconds})
        from picked p
       where d.event_id = p.event_id and d.consumer = p.consumer
      returning d.event_id::text as event_id, d.consumer, d.attempts::int as attempts`;
    if (claimed.length === 0) {
      return [];
    }

    const ids = [...new Set(claimed.map((c) => Number(c.event_id)))];
    const rows = await sql<EventRow[]>`
      select id::text as id, event_type, version::int as version, aggregate_type, aggregate_id, payload,
             occurred_at, correlation_id::text as correlation_id, causation_id::text as causation_id, actor
      from public.domain_events where id in ${sql(ids)}`;
    const events = new Map<number, StoredEvent>(
      rows.map((r) => [
        Number(r.id),
        {
          id: Number(r.id),
          type: r.event_type,
          version: r.version,
          aggregateType: r.aggregate_type,
          aggregateId: r.aggregate_id,
          payload: r.payload,
          occurredAt: r.occurred_at,
          correlationId: r.correlation_id,
          causationId: r.causation_id === null ? null : Number(r.causation_id),
          actor: r.actor,
        },
      ])
    );
    return claimed
      .map((c) => ({ eventId: Number(c.event_id), consumer: c.consumer, attempts: c.attempts, event: events.get(Number(c.event_id))! }))
      .filter((d) => d.event !== undefined)
      .sort((a, b) => a.eventId - b.eventId);
  }

  async ack(eventId: number, consumer: string): Promise<void> {
    const { sql } = this;
    await sql`
      update public.event_deliveries
         set status = 'done', processed_at = now(), locked_until = null, last_error = null
       where event_id = ${eventId} and consumer = ${consumer}`;
    // El evento queda «procesado» cuando todos sus consumidores terminaron (los muertos lo mantienen abierto).
    await sql`
      update public.domain_events set processed_at = now()
       where id = ${eventId} and processed_at is null
         and not exists (select 1 from public.event_deliveries where event_id = ${eventId} and status <> 'done')`;
  }

  async fail(eventId: number, consumer: string, error: string, retryInSeconds: number): Promise<void> {
    await this.sql`
      update public.event_deliveries
         set status = 'failed', last_error = ${error}, locked_until = null,
             next_attempt_at = now() + make_interval(secs => ${retryInSeconds})
       where event_id = ${eventId} and consumer = ${consumer}`;
  }

  async kill(eventId: number, consumer: string, error: string): Promise<void> {
    await this.sql`
      update public.event_deliveries
         set status = 'dead', last_error = ${error}, locked_until = null
       where event_id = ${eventId} and consumer = ${consumer}`;
  }

  async requeue(filter: { eventId?: number; consumer?: string }): Promise<number> {
    const { sql } = this;
    const byEvent = filter.eventId !== undefined ? sql`and event_id = ${filter.eventId}` : sql``;
    const byConsumer = filter.consumer !== undefined ? sql`and consumer = ${filter.consumer}` : sql``;
    const rows = await sql`
      update public.event_deliveries
         set status = 'pending', attempts = 0, next_attempt_at = now(), locked_until = null, last_error = null
       where status = 'dead' ${byEvent} ${byConsumer}
      returning event_id`;
    return rows.length;
  }

  async stats(): Promise<EventStats> {
    const { sql } = this;
    const counts = await sql<{ consumer: string; status: keyof ConsumerStats; n: number }[]>`
      select consumer, status, count(*)::int as n from public.event_deliveries group by consumer, status`;
    const [lag] = await sql<{ lag: number | null }[]>`
      select extract(epoch from (now() - min(e.occurred_at)))::int as lag
      from public.event_deliveries d join public.domain_events e on e.id = d.event_id
      where d.status in ('pending', 'processing', 'failed')`;
    const porConsumidor: Record<string, ConsumerStats> = {};
    let muertos = 0;
    for (const row of counts) {
      porConsumidor[row.consumer] ??= emptyStats();
      porConsumidor[row.consumer][row.status] = row.n;
      if (row.status === 'dead') muertos += row.n;
    }
    return { lagSegundos: Math.max(0, lag?.lag ?? 0), muertos, porConsumidor };
  }
}
