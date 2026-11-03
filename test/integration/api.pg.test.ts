// Prueba de integración: la API completa (router → controlador → servicio → repositorio) contra un
// Postgres REAL con las migraciones de db/migrations. No usa datos simulados en la capa de datos.
//
//   npm run db:up && DATABASE_URL=postgresql://postgres:postgres@localhost:54329/comparador npm run db:migrate
//   TEST_DATABASE_URL=postgresql://postgres:postgres@localhost:54329/comparador npm test
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { buildRouter } from '../../lib/app';
import { createSql, type Sql } from '../../lib/db/client';
import { PriceController } from '../../lib/prices/controller';
import { PgPriceRepository } from '../../lib/prices/repository';
import { PriceService } from '../../lib/prices/service';
import type { Offer, PriceProvider } from '../../lib/prices/types';
import { WatchController } from '../../lib/watches/controller';
import { PgWatchRepository } from '../../lib/watches/repository';
import { WatchService } from '../../lib/watches/service';

const URL = process.env.TEST_DATABASE_URL;
const RUN = `it_${Date.now()}`;

const PRODUCTO = { marca: 'Free Tea', nombre: `Frutos Rojos ${RUN}`, presentacion: '500ml', categoria: 'bebidas', tipo: 'té' };

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

describe.skipIf(!URL)('API v1 contra Postgres real', () => {
  let sql: Sql;
  let provider: StubProvider;
  let clock: Date;
  let router: ReturnType<typeof buildRouter>;
  const users = [`${RUN}_ana`, `${RUN}_beto`];

  const call = (method: string, path: string, opts: { user?: string; body?: unknown; query?: Record<string, string>; cron?: boolean } = {}) =>
    router.dispatch({
      method,
      path,
      query: opts.query ?? {},
      headers: opts.cron ? { authorization: 'Bearer secreto-de-prueba' } : { 'x-dev-user': opts.user ?? users[0] },
      body: opts.body,
    });

  const crear = async (body: unknown = { producto: PRODUCTO, precioObjetivo: 2.5 }, user = users[0]) => {
    const res = await call('POST', '/watches', { user, body });
    return { res, id: (res.body as { id: string }).id };
  };

  beforeAll(() => {
    sql = createSql(URL!);
    process.env.CRON_SECRET = 'secreto-de-prueba';
  });

  beforeEach(async () => {
    provider = new StubProvider();
    clock = new Date('2026-11-03T12:00:00Z');
    const watchRepo = new PgWatchRepository(sql);
    router = buildRouter(
      {
        watches: new WatchController(new WatchService(watchRepo)),
        prices: new PriceController(new PriceService(watchRepo, new PgPriceRepository(sql), provider, () => clock)),
      },
      async ({ headers }) => (typeof headers['x-dev-user'] === 'string' ? { clerkId: headers['x-dev-user'] } : null)
    );
    await cleanup();
  });

  // Primero las personas (arrastra seguimientos y alertas) y luego el producto compartido, que
  // se lleva sus publicaciones y su historial de precios: cada prueba parte de un producto sin historial.
  const cleanup = async () => {
    await sql`delete from public.app_users where clerk_id like ${RUN + '%'}`;
    await sql`delete from public.products where nombre::text like ${'%' + RUN}`;
  };

  afterAll(async () => {
    await sql`delete from public.app_users where clerk_id like ${RUN + '%'}`;
    await sql`delete from public.products where nombre::text like ${'%' + RUN}`;
    await sql`delete from public.domain_events where aggregate_type = 'watch' and aggregate_id not in (select id::text from public.watches)`;
    await sql.end();
  });

  it('CRUD completo: crea, lee, lista, edita y borra, persistiendo en la base', async () => {
    const { res, id } = await crear();
    expect(res.status).toBe(201);
    expect(res.body).toMatchObject({ estado: 'activa', precioObjetivo: 2.5, producto: { marca: 'Free Tea', tipo: 'té' } });

    const [{ n }] = await sql<{ n: number }[]>`select count(*)::int as n from public.watches where id = ${id}`;
    expect(n).toBe(1);

    expect((await call('GET', `/watches/${id}`)).status).toBe(200);
    expect((await call('GET', '/watches')).body).toMatchObject({ total: 1 });

    const patched = await call('PATCH', `/watches/${id}`, { body: { precioObjetivo: 2.0, estado: 'pausada' } });
    expect(patched.body).toMatchObject({ precioObjetivo: 2, estado: 'pausada' });
    const [row] = await sql<{ status_code: string; target_price: string }[]>`select status_code, target_price from public.watches where id = ${id}`;
    expect(row).toEqual({ status_code: 'pausada', target_price: '2.00' });

    expect((await call('DELETE', `/watches/${id}`)).status).toBe(204);
    expect((await call('GET', `/watches/${id}`)).status).toBe(404);
  });

  it('normalización: dos personas que siguen el mismo producto comparten UNA fila de producto', async () => {
    await crear(undefined, users[0]);
    await crear({ producto: { ...PRODUCTO, nombre: PRODUCTO.nombre.toUpperCase() }, precioObjetivo: null }, users[1]);

    const [{ n }] = await sql<{ n: number }[]>`
      select count(*)::int as n from public.products where nombre::text ilike ${'%' + RUN}`;
    expect(n).toBe(1);
  });

  it('un seguimiento repetido responde 409 y uno ajeno 404', async () => {
    const { id } = await crear();

    expect((await crear()).res.status).toBe(409);
    expect((await call('GET', `/watches/${id}`, { user: users[1] })).status).toBe(404);
    expect((await call('DELETE', `/watches/${id}`, { user: users[1] })).status).toBe(404);
  });

  it('check: guarda las ofertas, calcula el resumen y el historial refleja el cambio persistido', async () => {
    const { id } = await crear({ producto: PRODUCTO, precioObjetivo: 1.0 });
    provider.offers = [oferta(2.0), oferta(2.4, 'Wong')];

    const res = await call('POST', `/watches/${id}/check`);

    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ ofertasGuardadas: 2, ofertasDescartadas: 0, alertasNuevas: [] });
    expect((res.body as { seguimiento: unknown }).seguimiento).toMatchObject({
      resumenPrecios: { mejorPrecio: 2, tienda: 'Plaza Vea', minimoHistorico: 2, observaciones: 2 },
    });
    const [{ n }] = await sql<{ n: number }[]>`
      select count(*)::int as n from public.price_observations o
      join public.store_listings l on l.id = o.listing_id join public.watches w on w.product_id = l.product_id
      where w.id = ${id}`;
    expect(n).toBe(2);

    const history = await call('GET', `/watches/${id}/history`, { query: { days: '7' } });
    expect(history.body).toMatchObject({ dias: 7, estadisticas: { minimo: 2, maximo: 2.4, cantidad: 2 } });
  });

  it('R4: llegar al objetivo crea la alerta, pasa a alcanzada y genera eventos de dominio', async () => {
    const { id } = await crear({ producto: PRODUCTO, precioObjetivo: 2.5 });
    provider.offers = [oferta(2.2)];

    const res = await call('POST', `/watches/${id}/check`);

    const body = res.body as { alertasNuevas: Array<{ tipo: { codigo: string }; mensaje: string }>; seguimiento: { estado: string } };
    expect(body.seguimiento.estado).toBe('alcanzada');
    expect(body.alertasNuevas).toHaveLength(1);
    expect(body.alertasNuevas[0].tipo.codigo).toBe('objetivo_alcanzado');
    expect(body.alertasNuevas[0].mensaje).toContain('S/ 2.20');

    const events = await sql<{ event_type: string }[]>`
      select event_type from public.domain_events where aggregate_id = ${id} or payload->>'watchId' = ${id} order by id`;
    expect(events.map((e) => e.event_type)).toEqual(['watch.checked', 'alert.created']);
  });

  it('alertas: se listan, se filtran por no leídas y se marcan como leídas', async () => {
    const { id } = await crear({ producto: PRODUCTO, precioObjetivo: 2.5 });
    provider.offers = [oferta(2.2)];
    await call('POST', `/watches/${id}/check`);

    const unread = await call('GET', '/alerts', { query: { unread: 'true' } });
    expect(unread.body).toMatchObject({ total: 1, sinLeer: 1 });
    const alertId = (unread.body as { items: Array<{ id: number }> }).items[0].id;

    const marked = await call('PATCH', `/alerts/${alertId}`, { body: { leida: true } });
    expect(marked.body).toMatchObject({ id: alertId, leida: true });
    expect((await call('GET', '/alerts', { query: { unread: 'true' } })).body).toMatchObject({ total: 0, sinLeer: 0 });
    expect((await call('PATCH', `/alerts/${alertId}`, { user: users[1], body: { leida: true } })).status).toBe(404);
    expect((await call('PATCH', `/alerts/${alertId}`, { body: { leida: 'si' } })).status).toBe(400);
  });

  it('R3: una segunda revisión antes de 15 minutos responde 429 con Retry-After; después de 15, funciona', async () => {
    const { id } = await crear({ producto: PRODUCTO, precioObjetivo: 1 });
    provider.offers = [oferta(3)];
    await call('POST', `/watches/${id}/check`);
    // La marca de "última revisión" la pone la base con now(); el reloj de la prueba se alinea con ella.
    clock = new Date(Date.now() + 5 * 60 * 1000);

    const blocked = await call('POST', `/watches/${id}/check`);
    expect(blocked.status).toBe(429);
    expect(Number(blocked.headers?.['Retry-After'])).toBeGreaterThan(0);
    expect(provider.calls).toBe(1);

    clock = new Date(Date.now() + 16 * 60 * 1000);
    expect((await call('POST', `/watches/${id}/check`)).status).toBe(200);
  });

  it('un seguimiento pausado no se puede revisar (409)', async () => {
    const { id } = await crear();
    await call('PATCH', `/watches/${id}`, { body: { estado: 'pausada' } });

    const res = await call('POST', `/watches/${id}/check`);

    expect(res.status).toBe(409);
    expect(res.body).toMatchObject({ codigo: 'seguimiento_pausado' });
  });

  it('si las tiendas fallan responde 502 y no guarda nada', async () => {
    const { id } = await crear();
    provider.failWith = new Error('timeout');

    const res = await call('POST', `/watches/${id}/check`);

    expect(res.status).toBe(502);
    const [{ n }] = await sql<{ n: number }[]>`select count(*)::int as n from public.domain_events where aggregate_id = ${id}`;
    expect(n).toBe(0);
  });

  it('R6: un precio atípico se descarta y no contamina el historial', async () => {
    const { id } = await crear({ producto: PRODUCTO, precioObjetivo: 0.5 });
    for (const precio of [5.0, 5.2, 4.8]) {
      provider.offers = [oferta(precio)];
      clock = new Date(clock.getTime() + 20 * 60 * 1000);
      await call('POST', `/watches/${id}/check`);
      clock = new Date(Date.now() + 20 * 60 * 1000);
    }
    provider.offers = [oferta(0.9)];
    clock = new Date(Date.now() + 60 * 60 * 1000);

    const res = await call('POST', `/watches/${id}/check`);

    expect(res.body).toMatchObject({ ofertasGuardadas: 0, ofertasDescartadas: 1, alertasNuevas: [] });
    const history = await call('GET', `/watches/${id}/history`);
    expect((history.body as { estadisticas: { minimo: number } }).estadisticas.minimo).toBe(4.8);
  });

  it('atomicidad: si algo falla a mitad de la revisión, no queda ningún dato a medias', async () => {
    const { id } = await crear({ producto: PRODUCTO, precioObjetivo: 9 });
    // Una oferta con precio no positivo viola el CHECK de la base DESPUÉS de haber guardado la tienda y la publicación.
    provider.offers = [oferta(3.0), { ...oferta(3.1, 'Metro'), precio: -1 }];

    const res = await call('POST', `/watches/${id}/check`);

    expect(res.status).toBe(500);
    const [{ n }] = await sql<{ n: number }[]>`
      select count(*)::int as n from public.price_observations o
      join public.store_listings l on l.id = o.listing_id join public.watches w on w.product_id = l.product_id
      where w.id = ${id}`;
    expect(n).toBe(0);
    const [w] = await sql<{ last_checked_at: Date | null }[]>`select last_checked_at from public.watches where id = ${id}`;
    expect(w.last_checked_at).toBeNull();
  });

  it('cron: exige el secreto y revisa los seguimientos atrasados, aislando los errores', async () => {
    expect((await call('GET', '/internal/check-due')).status).toBe(401);

    const { id } = await crear({ producto: PRODUCTO, precioObjetivo: 2.5 });
    provider.offers = [oferta(2.1)];
    // Solo hay que revisar lo que nunca se revisó: se limpian los pendientes de otras pruebas.
    await sql`update public.watches set last_checked_at = now() where id <> ${id}`;

    const res = await call('GET', '/internal/check-due', { cron: true });

    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ revisados: 1, alertas: 1, errores: 0 });
    expect((await call('GET', `/watches/${id}`)).body).toMatchObject({ estado: 'alcanzada' });
    expect((await call('POST', '/internal/check-due', { cron: true })).body).toMatchObject({ revisados: 0 });
  });

  it('borrar un seguimiento arrastra sus alertas (cascada) pero conserva el historial de precios del producto', async () => {
    const { id } = await crear({ producto: PRODUCTO, precioObjetivo: 2.5 });
    provider.offers = [oferta(2.2)];
    await call('POST', `/watches/${id}/check`);

    await call('DELETE', `/watches/${id}`);

    const [alerts] = await sql<{ n: number }[]>`select count(*)::int as n from public.alerts where watch_id = ${id}`;
    const [prices] = await sql<{ n: number }[]>`
      select count(*)::int as n from public.price_observations o join public.store_listings l on l.id = o.listing_id
      join public.products p on p.id = l.product_id where p.nombre::text like ${'%' + RUN}`;
    expect(alerts.n).toBe(0);
    expect(prices.n).toBe(1);
  });
});
