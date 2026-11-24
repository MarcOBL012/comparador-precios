// Integración del bus de eventos contra un Postgres REAL con las migraciones de db/migrations
// (incluida la 006): outbox atómico, fan-out por consumidor, reclamo concurrente sin duplicados,
// arrendamientos, orden por agregado, reintentos hasta la cola de fallos y reposición.
//
//   npm run db:up && DATABASE_URL=postgresql://postgres:postgres@localhost:54329/comparador npm run db:migrate
//   TEST_DATABASE_URL=postgresql://postgres:postgres@localhost:54329/comparador npm test
import { randomUUID } from 'node:crypto';
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { createSql, type Sql } from '../../lib/db/client';
import { PgEventBus } from '../../lib/events/bus';
import { MetricsRecorder } from '../../lib/events/consumers/metricsRecorder';
import { EventDispatcher } from '../../lib/events/dispatcher';
import { PgEventQueue } from '../../lib/events/repository';
import { PermanentError } from '../../lib/events/retryPolicy';
import { ConsumerRegistry } from '../../lib/events/subscriptions';
import type { Consumer, StoredEvent } from '../../lib/events/types';

const URL = process.env.TEST_DATABASE_URL;
const RUN = `it_ev_${Date.now()}`;
const CONSUMER = 'it_spy';

class Spy implements Consumer {
  readonly name = CONSUMER;
  readonly handles = ['watch.checked'] as const;
  readonly seen: number[] = [];
  failWith: Error | null = null;
  async handle(e: StoredEvent): Promise<void> {
    this.seen.push(e.id);
    if (this.failWith) throw this.failWith;
  }
}

describe.skipIf(!URL)('bus de eventos contra Postgres real', () => {
  let sql: Sql;
  let other: Sql; // segunda conexión: el reclamo concurrente necesita dos conexiones de verdad
  let queue: PgEventQueue;
  let spy: Spy;
  let registry: ConsumerRegistry;
  let bus: PgEventBus;
  let dispatcher: EventDispatcher;

  const checked = (n: number, extra: Record<string, unknown> = {}) => ({
    type: 'watch.checked' as const,
    aggregateType: 'watch',
    aggregateId: `${RUN}_${n}`,
    payload: { ofertas: n, descartadas: 0 },
    ...extra,
  });
  const publish = (n: number, extra: Record<string, unknown> = {}) => sql.begin((tx) => bus.publish(tx, checked(n, extra)));
  const delivery = async (eventId: number) =>
    (await sql<{ status: string; attempts: number; last_error: string | null }[]>`
      select status, attempts, last_error from public.event_deliveries where event_id = ${eventId} and consumer = ${CONSUMER}`)[0];
  const forceDue = (eventId: number) =>
    sql`update public.event_deliveries set next_attempt_at = now() - interval '1 second' where event_id = ${eventId}`;
  const cleanup = () => sql`delete from public.domain_events where aggregate_id like ${RUN + '%'}`;

  beforeAll(() => {
    sql = createSql(URL!);
    other = createSql(URL!);
  });

  beforeEach(async () => {
    await cleanup();
    spy = new Spy();
    registry = new ConsumerRegistry([spy, new MetricsRecorder(() => {})]);
    bus = new PgEventBus((type) => registry.consumersFor(type), () => true);
    queue = new PgEventQueue(sql);
    dispatcher = new EventDispatcher(queue, registry, { log: () => {}, random: () => 0.5 });
  });

  afterAll(async () => {
    await cleanup();
    await sql.end();
    await other.end();
  });

  it('outbox atómico: si la transacción se revierte, no queda ni el evento ni sus entregas', async () => {
    await expect(
      sql.begin(async (tx) => {
        await bus.publish(tx, checked(1));
        throw new Error('algo falló después de publicar');
      })
    ).rejects.toThrow('algo falló');
    const [{ n }] = await sql<{ n: number }[]>`select count(*)::int as n from public.domain_events where aggregate_id = ${RUN + '_1'}`;
    expect(n).toBe(0);
  });

  it('al confirmar guarda el evento con su envelope y una entrega por cada consumidor suscrito', async () => {
    const correlationId = randomUUID();
    const id = await publish(1, { correlationId, actor: 'user:ana' });
    expect(id).toBeTypeOf('number');

    const [row] = await sql<{ version: number; actor: string; correlation_id: string; payload: unknown }[]>`
      select version, actor, correlation_id::text as correlation_id, payload from public.domain_events where id = ${id!}`;
    expect(row).toMatchObject({ version: 1, actor: 'user:ana', correlation_id: correlationId, payload: { ofertas: 1, descartadas: 0 } });

    const deliveries = await sql<{ consumer: string; status: string }[]>`
      select consumer, status from public.event_deliveries where event_id = ${id!} order by consumer`;
    expect(deliveries).toEqual([
      { consumer: CONSUMER, status: 'pending' },
      { consumer: 'metrics', status: 'pending' },
    ]);
  });

  it('con el despacho apagado se comporta como antes: solo la bandeja, sin entregas', async () => {
    const legacy = new PgEventBus((t) => registry.consumersFor(t), () => false);
    const id = await sql.begin((tx) => legacy.publish(tx, checked(1)));
    const [{ n }] = await sql<{ n: number }[]>`select count(*)::int as n from public.event_deliveries where event_id = ${id!}`;
    expect(n).toBe(0);
    const [row] = await sql<{ version: number; actor: string }[]>`select version, actor from public.domain_events where id = ${id!}`;
    expect(row).toEqual({ version: 1, actor: 'system' });
  });

  it('dedupeKey: el mismo hecho no se publica dos veces ni duplica entregas', async () => {
    const key = `${RUN}-hora-11`;
    const first = await publish(1, { dedupeKey: key });
    const second = await publish(1, { dedupeKey: key });
    expect(first).toBeTypeOf('number');
    expect(second).toBeNull();
    const [{ n }] = await sql<{ n: number }[]>`select count(*)::int as n from public.domain_events where dedupe_key = ${key}`;
    expect(n).toBe(1);
  });

  it('rechaza publicar una carga útil que rompe el contrato, sin dejar nada a medias', async () => {
    await expect(sql.begin((tx) => bus.publish(tx, { ...checked(1), payload: { ofertas: 'x' } as never }))).rejects.toThrow(/inválida/);
    const [{ n }] = await sql<{ n: number }[]>`select count(*)::int as n from public.domain_events where aggregate_id = ${RUN + '_1'}`;
    expect(n).toBe(0);
  });

  it('dos despachadores simultáneos nunca reciben la misma entrega (SKIP LOCKED)', async () => {
    for (let n = 1; n <= 8; n++) await publish(n);
    const [a, b] = await Promise.all([queue.claim(100, 60), new PgEventQueue(other).claim(100, 60)]);
    const mine = [...a, ...b].filter((d) => d.consumer === CONSUMER).map((d) => d.eventId);
    expect(new Set(mine).size).toBe(mine.length); // ninguna repetida
    expect(mine).toHaveLength(8); // y no se perdió ninguna
  });

  it('recupera una entrega cuya función murió: al vencer el arrendamiento se reclama de nuevo', async () => {
    const id = (await publish(1))!;
    expect((await queue.claim(10, 60)).filter((d) => d.eventId === id)).toHaveLength(2); // spy + metrics
    expect(await queue.claim(10, 60)).toHaveLength(0); // arrendamiento vigente
    await sql`update public.event_deliveries set locked_until = now() - interval '1 second' where event_id = ${id}`;
    const again = await queue.claim(10, 60);
    expect(again.map((d) => d.attempts)).toEqual([2, 2]);
  });

  it('orden por agregado: el evento 2 no se entrega hasta que el 1 termine', async () => {
    const aggregate = `${RUN}_orden`;
    const first = (await sql.begin((tx) => bus.publish(tx, { ...checked(1), aggregateId: aggregate })))!;
    const second = (await sql.begin((tx) => bus.publish(tx, { ...checked(2), aggregateId: aggregate })))!;

    const batch1 = (await queue.claim(10, 60)).filter((d) => d.consumer === CONSUMER);
    expect(batch1.map((d) => d.eventId)).toEqual([first]);
    expect((await queue.claim(10, 60)).filter((d) => d.consumer === CONSUMER)).toHaveLength(0); // el 2 sigue esperando

    await queue.ack(first, CONSUMER);
    expect((await queue.claim(10, 60)).filter((d) => d.consumer === CONSUMER).map((d) => d.eventId)).toEqual([second]);
  });

  it('despacha de punta a punta: entrega, marca hecho y deja el evento como procesado', async () => {
    const id = (await publish(1))!;
    const summary = await dispatcher.run();
    expect(summary).toMatchObject({ hechas: 2, reintentos: 0, muertas: 0 });
    expect(spy.seen).toEqual([id]);
    expect(await delivery(id)).toMatchObject({ status: 'done', attempts: 1 });
    const [{ processed }] = await sql<{ processed: boolean }[]>`select processed_at is not null as processed from public.domain_events where id = ${id}`;
    expect(processed).toBe(true);
  });

  it('un consumidor que falla se reintenta con espera y, tras 6 intentos, va a la cola de fallos', async () => {
    spy.failWith = new Error('503 de la tienda');
    const id = (await publish(1))!;

    await dispatcher.run();
    expect(await delivery(id)).toMatchObject({ status: 'failed', attempts: 1 });
    expect((await delivery(id)).last_error).toMatch(/503/);
    const [{ future }] = await sql<{ future: boolean }[]>`
      select next_attempt_at > now() + interval '20 seconds' as future from public.event_deliveries where event_id = ${id} and consumer = ${CONSUMER}`;
    expect(future).toBe(true); // la espera (30 s) todavía no se cumplió
    expect((await dispatcher.run()).reclamadas).toBe(0);

    for (let intento = 2; intento <= 6; intento++) {
      await forceDue(id);
      await dispatcher.run();
    }
    expect(await delivery(id)).toMatchObject({ status: 'dead', attempts: 6 });
    // El consumidor sano del mismo evento no se vio afectado.
    const [metrics] = await sql<{ status: string }[]>`select status from public.event_deliveries where event_id = ${id} and consumer = 'metrics'`;
    expect(metrics.status).toBe('done');
  });

  it('un error permanente va directo a la cola de fallos y requeue lo devuelve a pendiente', async () => {
    spy.failWith = new PermanentError('payload imposible');
    const id = (await publish(1))!;
    await dispatcher.run();
    expect(await delivery(id)).toMatchObject({ status: 'dead', attempts: 1 });

    expect((await queue.stats()).muertos).toBeGreaterThanOrEqual(1);
    spy.failWith = null;
    expect(await queue.requeue({ eventId: id })).toBe(1);
    expect(await delivery(id)).toMatchObject({ status: 'pending', attempts: 0 });
    await dispatcher.run();
    expect(await delivery(id)).toMatchObject({ status: 'done' });
  });

  it('las estadísticas cuentan entregas por consumidor y estado, y el retraso del más antiguo', async () => {
    const id = (await publish(1))!;
    await sql`update public.domain_events set occurred_at = now() - interval '5 minutes' where id = ${id}`;
    const stats = await queue.stats();
    expect(stats.porConsumidor[CONSUMER].pending).toBeGreaterThanOrEqual(1);
    expect(stats.lagSegundos).toBeGreaterThanOrEqual(299);
  });
});
