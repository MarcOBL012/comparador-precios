// Fase 2 contra un Postgres REAL: alertas y revisiones por eventos (AlertEvaluator, WatchChecker,
// scheduler y eventos watch.*). La suite de la API (api.pg.test.ts) prueba aparte que el contrato HTTP
// es idéntico; aquí se prueba lo que solo existe en el modo por eventos.
//
//   TEST_DATABASE_URL=postgresql://postgres:postgres@localhost:54329/comparador npm test
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { buildDomain, type Domain } from '../../lib/compose';
import { createSql, type Sql } from '../../lib/db/client';
import { UpstreamError } from '../../lib/errors';
import { ALERT_EVALUATOR } from '../../lib/events/consumers/alertEvaluator';
import { checkDedupeKey } from '../../lib/events/scheduler';
import type { Offer, PriceProvider } from '../../lib/prices/types';

const URL = process.env.TEST_DATABASE_URL;
const RUN = `it_f2_${Date.now()}`;
const ANA = `${RUN}_ana`;
const LUIS = `${RUN}_luis`;

class StubProvider implements PriceProvider {
  offers: Offer[] = [];
  failWith: Error | null = null;
  calls = 0;
  async fetchOffers(): Promise<Offer[]> {
    this.calls += 1;
    if (this.failWith) throw this.failWith;
    return this.offers;
  }
}

const oferta = (precio: number, tienda = 'Plaza Vea'): Offer => ({
  tienda,
  titulo: 'Té Negro FREE TEA Frutos Rojos Botella 500ml',
  precio,
  url: 'https://www.plazavea.com.pe/free-tea/p',
  fuente: 'directa',
});

const producto = (n = 0) => ({ marca: 'Free Tea', nombre: `Frutos Rojos ${n} ${RUN}`, presentacion: '500ml', categoria: 'bebidas' as const, tipo: 'té' });

describe.skipIf(!URL)('alertas y revisiones por eventos contra Postgres real', () => {
  let sql: Sql;
  let provider: StubProvider;
  let domain: Domain;
  let clock: Date;

  const alertsOf = (watchId: string) =>
    sql<{ alert_type_code: string; observation_id: string }[]>`select alert_type_code, observation_id::text from public.alerts where watch_id = ${watchId} order by id`;
  const eventTypes = async (aggregateId: string) =>
    (await sql<{ event_type: string }[]>`select event_type from public.domain_events where aggregate_id = ${aggregateId} order by id`).map((r) => r.event_type);
  /** Cada revisión necesita 15 min desde la anterior (R3): el reloj se adelanta respecto de la marca de la base. */
  const advance = () => {
    clock = new Date(Date.now() + 30 * 60 * 1000 + (clock.getTime() % 1000));
  };
  const check = async (user: string, watchId: string) => {
    advance();
    return domain.priceService.check(user, watchId);
  };
  const dispatch = () => domain.dispatcher.run();

  const cleanup = async () => {
    const watches = await sql<{ id: string }[]>`
      select w.id::text as id from public.watches w join public.app_users u on u.id = w.user_id where u.clerk_id like ${RUN + '%'}`;
    const ids = watches.map((w) => w.id);
    if (ids.length > 0) {
      await sql`delete from public.domain_events where aggregate_id in ${sql(ids)} or payload->>'watchId' in ${sql(ids)}`;
    }
    await sql`delete from public.app_users where clerk_id like ${RUN + '%'}`;
    await sql`delete from public.products where nombre::text like ${'%' + RUN}`;
  };

  beforeAll(() => {
    sql = createSql(URL!);
  });

  beforeEach(async () => {
    await cleanup();
    provider = new StubProvider();
    clock = new Date();
    domain = buildDomain({ sql, provider, events: true, now: () => clock, log: () => {}, dispatcherOptions: { log: () => {}, random: () => 0.5 } });
  });

  afterAll(async () => {
    await cleanup();
    await sql.end();
  });

  it('la revisión guarda las ofertas y publica un watch.checked completo, pero NO crea alertas por sí sola', async () => {
    const w = await domain.watchService.create(ANA, producto(), 2.5);
    provider.offers = [oferta(2.2), oferta(3.1, 'Wong')];

    const outcome = await domain.prices.recordObservations(
      { id: w.id, userId: (await sql<{ id: string }[]>`select user_id::text as id from public.watches where id = ${w.id}`)[0].id, productId: w.producto.id, precioObjetivo: 2.5, estado: 'activa' },
      provider.offers
    );

    expect(outcome).toMatchObject({ guardadas: 2, descartadas: 0, mejor: { precio: 2.2, tienda: 'Plaza Vea' }, statsPrevias: { observaciones: 0 } });
    expect(await alertsOf(w.id)).toHaveLength(0); // las alertas las decide el consumidor, después
    const [event] = await sql<{ payload: Record<string, unknown> }[]>`select payload from public.domain_events where aggregate_id = ${w.id} and event_type = 'watch.checked'`;
    expect(event.payload).toMatchObject({ ofertas: 2, descartadas: 0, productId: w.producto.id, mejor: { precio: 2.2 }, statsPrevias: { observaciones: 0, minimo: null } });
  });

  it('una sola revisión alerta a TODOS los que siguen el producto, aunque nunca la hayan pedido', async () => {
    const ana = await domain.watchService.create(ANA, producto(), 2.5);
    const luis = await domain.watchService.create(LUIS, producto(), 2.2); // mismo producto, otra persona
    expect(luis.producto.id).toBe(ana.producto.id);
    provider.offers = [oferta(2.1)];

    const result = await check(ANA, ana.id); // solo Ana revisa

    expect(result.alertasNuevas).toHaveLength(1); // el contrato de «Revisar ahora» no cambia
    expect((await alertsOf(luis.id)).map((a) => a.alert_type_code)).toEqual(['objetivo_alcanzado']);
    const luisAfter = await domain.watchService.get(LUIS, luis.id);
    expect(luisAfter.estado).toBe('alcanzada');
    expect(luisAfter.ultimaRevision).toBeNull(); // Luis jamás revisó nada
  });

  it('un seguimiento pausado no recibe alertas, y uno activo sin objetivo alcanzado tampoco', async () => {
    const ana = await domain.watchService.create(ANA, producto(), 2.5);
    const luis = await domain.watchService.create(LUIS, producto(), 2.5);
    await domain.watchService.update(LUIS, luis.id, { estado: 'pausada' });
    const beto = await domain.watchService.create(`${RUN}_beto`, producto(), 1.0); // objetivo que no se alcanza
    provider.offers = [oferta(2.1)];

    await check(ANA, ana.id);

    expect(await alertsOf(luis.id)).toHaveLength(0);
    expect(await alertsOf(beto.id)).toHaveLength(0);
    expect((await domain.watchService.get(`${RUN}_beto`, beto.id)).estado).toBe('activa');
  });

  it('es idempotente: reprocesar el mismo evento no duplica alertas ni eventos', async () => {
    const ana = await domain.watchService.create(ANA, producto(), 2.5);
    provider.offers = [oferta(2.1)];
    await check(ANA, ana.id);
    expect(await alertsOf(ana.id)).toHaveLength(1);

    // Se vuelve a entregar el watch.checked al evaluador (como si el confirmar hubiera fallado).
    await sql`update public.event_deliveries set status = 'pending', attempts = 0
              where consumer = ${ALERT_EVALUATOR} and event_id in (select id from public.domain_events where aggregate_id = ${ana.id})`;
    await dispatch();

    expect(await alertsOf(ana.id)).toHaveLength(1);
    expect((await eventTypes(ana.id)).filter((t) => t === 'watch.checked')).toHaveLength(1);
    const [{ n }] = await sql<{ n: number }[]>`select count(*)::int as n from public.domain_events where event_type = 'alert.created' and payload->>'watchId' = ${ana.id}`;
    expect(n).toBe(1);
  });

  it('encadena los eventos: alert.created lleva la misma correlación y como causa al watch.checked', async () => {
    const ana = await domain.watchService.create(ANA, producto(), 2.5);
    provider.offers = [oferta(2.1)];
    await check(ANA, ana.id);

    const [checked] = await sql<{ id: string; correlation_id: string; actor: string }[]>`
      select id::text as id, correlation_id::text as correlation_id, actor from public.domain_events where aggregate_id = ${ana.id} and event_type = 'watch.checked'`;
    const [alert] = await sql<{ causation_id: string; correlation_id: string }[]>`
      select causation_id::text as causation_id, correlation_id::text as correlation_id from public.domain_events where event_type = 'alert.created' and payload->>'watchId' = ${ana.id}`;
    expect(checked.correlation_id).toBeTruthy();
    expect(checked.actor).toMatch(/^user:/);
    expect(alert).toEqual({ causation_id: checked.id, correlation_id: checked.correlation_id });
  });

  it('minimo_historico: un precio por debajo del mínimo (con ≥ 3 observaciones previas) genera la alerta', async () => {
    const ana = await domain.watchService.create(ANA, producto(), 0.5);
    for (const precio of [5.0, 5.2, 4.8]) {
      provider.offers = [oferta(precio)];
      await check(ANA, ana.id);
    }
    expect(await alertsOf(ana.id)).toHaveLength(0);

    provider.offers = [oferta(4.0)];
    const result = await check(ANA, ana.id);

    expect(result.alertasNuevas.map((a) => a.tipo.codigo)).toEqual(['minimo_historico']);
  });

  it('R6: el precio atípico se descarta antes de publicar, igual que en el modo anterior', async () => {
    const ana = await domain.watchService.create(ANA, producto(), 0.5);
    for (const precio of [5.0, 5.2, 4.8]) {
      provider.offers = [oferta(precio)];
      await check(ANA, ana.id);
    }
    provider.offers = [oferta(0.9)]; // < 40 % de la mediana

    const result = await check(ANA, ana.id);

    expect(result).toMatchObject({ ofertasGuardadas: 0, ofertasDescartadas: 1, alertasNuevas: [] });
  });

  describe('cambio de objetivo', () => {
    it('avisa al instante si el mejor precio actual ya cumple el nuevo objetivo', async () => {
      const ana = await domain.watchService.create(ANA, producto(), 1.0);
      provider.offers = [oferta(3.0)];
      await check(ANA, ana.id);
      expect(await alertsOf(ana.id)).toHaveLength(0);

      await domain.watchService.update(ANA, ana.id, { precioObjetivo: 3.5 });
      expect(await alertsOf(ana.id)).toHaveLength(0); // todavía no: el evento se entrega después
      await dispatch();

      expect((await alertsOf(ana.id)).map((a) => a.alert_type_code)).toEqual(['objetivo_alcanzado']);
      expect((await domain.watchService.get(ANA, ana.id)).estado).toBe('alcanzada');
    });

    it('no avisa si el precio sigue por encima del objetivo, ni si se quita el objetivo', async () => {
      const ana = await domain.watchService.create(ANA, producto(), 1.0);
      provider.offers = [oferta(3.0)];
      await check(ANA, ana.id);

      await domain.watchService.update(ANA, ana.id, { precioObjetivo: 2.0 });
      await domain.watchService.update(ANA, ana.id, { precioObjetivo: null });
      await dispatch();

      expect(await alertsOf(ana.id)).toHaveLength(0);
      expect((await domain.watchService.get(ANA, ana.id)).estado).toBe('activa');
    });
  });

  describe('eventos del ciclo de vida del seguimiento', () => {
    it('crear, cambiar objetivo, pausar, reactivar y borrar publican su evento', async () => {
      const ana = await domain.watchService.create(ANA, producto(), 2.5);
      await domain.watchService.update(ANA, ana.id, { precioObjetivo: 2.0 });
      await domain.watchService.update(ANA, ana.id, { estado: 'pausada' });
      await domain.watchService.update(ANA, ana.id, { estado: 'activa' });
      await domain.watchService.update(ANA, ana.id, { precioObjetivo: 2.0 }); // mismo valor: no es un cambio
      await domain.watchService.remove(ANA, ana.id);

      expect(await eventTypes(ana.id)).toEqual(['watch.created', 'watch.target_changed', 'watch.paused', 'watch.reactivated', 'watch.deleted']);
      const [changed] = await sql<{ payload: Record<string, unknown> }[]>`select payload from public.domain_events where aggregate_id = ${ana.id} and event_type = 'watch.target_changed'`;
      expect(changed.payload).toMatchObject({ anterior: 2.5, nuevo: 2 });
    });

    it('un alta rechazada (409) no deja ningún evento: el evento y el cambio van juntos', async () => {
      const ana = await domain.watchService.create(ANA, producto(), 2.5);
      await expect(domain.watchService.create(ANA, producto(), 2.5)).rejects.toMatchObject({ codigo: 'ya_seguido' });
      expect((await eventTypes(ana.id)).filter((t) => t === 'watch.created')).toHaveLength(1);
      const [{ n }] = await sql<{ n: number }[]>`select count(*)::int as n from public.domain_events where event_type = 'watch.created' and payload->>'watchId' <> ${ana.id} and actor = ${'user:' + (await sql<{ id: string }[]>`select user_id::text as id from public.watches where id = ${ana.id}`)[0].id}`;
      expect(n).toBe(0);
    });
  });

  describe('scheduler y revisión programada (el cron)', () => {
    /** Solo deben estar vencidos los seguimientos de esta prueba (se descartan los de otras). */
    const isolate = () => sql`update public.watches w set last_checked_at = now() from public.app_users u where u.id = w.user_id and u.clerk_id not like ${RUN + '%'}`;

    it('revisa más de 5 seguimientos en una corrida (el modo anterior se detenía en 5)', async () => {
      const ids: string[] = [];
      for (let n = 1; n <= 8; n++) ids.push((await domain.watchService.create(ANA, producto(n), 9)).id);
      provider.offers = [oferta(2.1)];
      await isolate();

      const summary = await domain.priceService.checkDue();

      expect(summary).toMatchObject({ programados: 8, revisados: 8, errores: 0 });
      for (const id of ids) expect((await domain.watchService.get(ANA, id)).ultimaRevision).not.toBeNull();
      expect(provider.calls).toBe(8);
    });

    it('el cron entrega alertas, también a quien siguió el producto pero no lo revisó', async () => {
      const ana = await domain.watchService.create(ANA, producto(), 2.5);
      const luis = await domain.watchService.create(LUIS, producto(), 2.5);
      provider.offers = [oferta(2.1)];
      await isolate();

      const summary = await domain.priceService.checkDue();

      // Se revisa una vez por seguimiento vencido, pero cada alerta se crea una sola vez por seguimiento.
      expect(summary.errores).toBe(0);
      expect(await alertsOf(ana.id)).toHaveLength(1);
      expect(await alertsOf(luis.id)).toHaveLength(1);
      expect(summary.alertas).toBe(2);
    });

    it('no repite la petición dentro de la misma ventana de 6 h (dedupe) ni revisa lo ya revisado', async () => {
      const ana = await domain.watchService.create(ANA, producto(), 9);
      provider.failWith = new UpstreamError('las tiendas no responden');
      await isolate();

      const first = await domain.priceService.checkDue();
      const second = await domain.priceService.checkDue(); // sigue vencido (falló), pero ya está pedido

      expect(first).toMatchObject({ programados: 1, revisados: 0, errores: 1 });
      expect(second).toMatchObject({ programados: 0 });
      const [{ n }] = await sql<{ n: number }[]>`select count(*)::int as n from public.domain_events where aggregate_id = ${ana.id} and event_type = 'watch.check_requested'`;
      expect(n).toBe(1);
      expect(checkDedupeKey(ana.id, clock)).toMatch(new RegExp(`^check:${ana.id}:\\d+$`));
    });

    it('si las tiendas fallan, la revisión se reintenta con espera y se completa cuando vuelven (antes se perdía hasta mañana)', async () => {
      const ana = await domain.watchService.create(ANA, producto(), 2.5);
      provider.failWith = new UpstreamError('las tiendas no responden');
      await isolate();
      await domain.priceService.checkDue();

      const [delivery] = await sql<{ status: string; attempts: number; event_id: string }[]>`
        select d.status, d.attempts, d.event_id::text as event_id from public.event_deliveries d join public.domain_events e on e.id = d.event_id
        where e.aggregate_id = ${ana.id} and d.consumer = 'watch_checker'`;
      expect(delivery).toMatchObject({ status: 'failed', attempts: 1 });
      expect((await domain.watchService.get(ANA, ana.id)).ultimaRevision).toBeNull();

      provider.failWith = null;
      provider.offers = [oferta(2.1)];
      await sql`update public.event_deliveries set next_attempt_at = now() - interval '1 second' where event_id = ${delivery.event_id}`;
      await dispatch();

      const after = await domain.watchService.get(ANA, ana.id);
      expect(after.ultimaRevision).not.toBeNull();
      expect(after.estado).toBe('alcanzada'); // y la alerta salió, encadenada a la misma correlación
      expect(await alertsOf(ana.id)).toHaveLength(1);
    });

    it('un seguimiento pausado mientras esperaba su revisión programada se ignora', async () => {
      const ana = await domain.watchService.create(ANA, producto(), 2.5);
      provider.failWith = new UpstreamError('caído');
      await isolate();
      await domain.priceService.checkDue();
      await domain.watchService.update(ANA, ana.id, { estado: 'pausada' });
      provider.failWith = null;
      provider.offers = [oferta(2.1)];
      const callsBefore = provider.calls;
      await sql`update public.event_deliveries set next_attempt_at = now() - interval '1 second' where consumer = 'watch_checker' and event_id in (select id from public.domain_events where aggregate_id = ${ana.id})`;

      await dispatch();

      expect(provider.calls).toBe(callsBefore); // no se consultaron las tiendas
      expect(await alertsOf(ana.id)).toHaveLength(0);
    });
  });

  describe('despacho acotado', () => {
    it('reclamar con filtro de correlación y de consumidor solo toca lo pedido', async () => {
      const ana = await domain.watchService.create(ANA, producto(), 9);
      provider.offers = [oferta(2.1)];
      advance();
      // Se publica la revisión SIN despachar (se hace a mano abajo).
      const user = (await sql<{ id: string }[]>`select user_id::text as id from public.watches where id = ${ana.id}`)[0].id;
      await domain.prices.recordObservations(
        { id: ana.id, userId: user, productId: ana.producto.id, precioObjetivo: 9, estado: 'activa' },
        provider.offers,
        { correlationId: '11111111-1111-4111-8111-111111111111' }
      );

      expect(await domain.queue.claim(10, 60, { correlationId: '22222222-2222-4222-8222-222222222222' })).toHaveLength(0);
      const onlyEvaluator = await domain.queue.claim(10, 60, { correlationId: '11111111-1111-4111-8111-111111111111', consumers: [ALERT_EVALUATOR] });
      expect(onlyEvaluator.map((d) => d.consumer)).toEqual([ALERT_EVALUATOR]);
    });
  });
});
