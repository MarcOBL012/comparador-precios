import { beforeEach, describe, expect, it } from 'vitest';
import { buildRouter } from '../../lib/app';
import { WatchController } from '../../lib/watches/controller';
import { WatchService } from '../../lib/watches/service';
import { FakeWatchRepository } from './fakeRepository';

const BODY = {
  producto: { marca: 'Free Tea', nombre: 'Frutos Rojos', presentacion: '500ml', categoria: 'bebidas', tipo: 'té' },
  precioObjetivo: 2.5,
};

describe('API v1 /watches (router + controlador + servicio)', () => {
  let router: ReturnType<typeof buildRouter>;

  const call = (
    method: string,
    path: string,
    opts: { user?: string | null; body?: unknown; query?: Record<string, string> } = {}
  ) =>
    router.dispatch({
      method,
      path,
      query: opts.query ?? {},
      headers: opts.user === null ? {} : { 'x-dev-user': opts.user ?? 'ana' },
      body: opts.body,
    });

  beforeEach(() => {
    const repo = new FakeWatchRepository();
    const controllers = { watches: new WatchController(new WatchService(repo, 20)) };
    // La autenticación de prueba lee el encabezado x-dev-user; la real (Clerk) se prueba en auth.test.ts.
    router = buildRouter(controllers, async ({ headers }) => {
      const id = headers['x-dev-user'];
      return typeof id === 'string' ? { clerkId: id } : null;
    });
  });

  it('POST crea el seguimiento: 201 con Location y el recurso', async () => {
    const res = await call('POST', '/watches', { body: BODY });

    expect(res.status).toBe(201);
    expect(res.headers?.Location).toMatch(/^\/api\/v1\/watches\/[0-9a-f-]{36}$/);
    expect(res.body).toMatchObject({ estado: 'activa', precioObjetivo: 2.5 });
  });

  it('POST sin sesión responde 401', async () => {
    const res = await call('POST', '/watches', { user: null, body: BODY });

    expect(res.status).toBe(401);
    expect(res.body).toMatchObject({ codigo: 'no_autenticado' });
  });

  it('POST con un cuerpo inválido responde 400 con el detalle por campo', async () => {
    const res = await call('POST', '/watches', {
      body: { producto: { nombre: '', categoria: 'inventada' }, precioObjetivo: -3 },
    });

    expect(res.status).toBe(400);
    const campos = (res.body as { detalles: { campo: string }[] }).detalles.map((d) => d.campo);
    expect(campos).toEqual(expect.arrayContaining(['producto.nombre', 'producto.categoria', 'precioObjetivo']));
  });

  it('POST rechaza campos desconocidos (el cliente no puede mandar userId ni estado)', async () => {
    const res = await call('POST', '/watches', { body: { ...BODY, userId: 'otro', estado: 'alcanzada' } });

    expect(res.status).toBe(400);
  });

  it('POST rechaza más de 2 decimales y un cuerpo que no es un objeto', async () => {
    expect((await call('POST', '/watches', { body: { ...BODY, precioObjetivo: 2.555 } })).status).toBe(400);
    expect((await call('POST', '/watches', { body: 'esto no es json' })).status).toBe(400);
  });

  it('POST repetido responde 409 ya_seguido', async () => {
    await call('POST', '/watches', { body: BODY });
    const res = await call('POST', '/watches', { body: BODY });

    expect(res.status).toBe(409);
    expect(res.body).toMatchObject({ codigo: 'ya_seguido' });
  });

  it('GET lista solo lo de la persona y valida los parámetros', async () => {
    await call('POST', '/watches', { body: BODY });
    await call('POST', '/watches', { user: 'beto', body: BODY });

    const mine = await call('GET', '/watches');
    expect(mine.status).toBe(200);
    expect(mine.body).toMatchObject({ total: 1, limit: 20, offset: 0 });

    expect((await call('GET', '/watches', { query: { limit: '500' } })).status).toBe(400);
    expect((await call('GET', '/watches', { query: { estado: 'volando' } })).status).toBe(400);
  });

  it('GET /watches/{id}: 200, 404 si no existe, 400 si el id no es un UUID', async () => {
    const created = await call('POST', '/watches', { body: BODY });
    const id = (created.body as { id: string }).id;

    expect((await call('GET', `/watches/${id}`)).status).toBe(200);
    expect((await call('GET', '/watches/00000000-0000-4000-8000-999999999999')).status).toBe(404);
    expect((await call('GET', '/watches/no-es-uuid')).status).toBe(400);
  });

  it('un seguimiento de otra persona responde 404 (no 403)', async () => {
    const created = await call('POST', '/watches', { body: BODY });
    const id = (created.body as { id: string }).id;

    expect((await call('GET', `/watches/${id}`, { user: 'beto' })).status).toBe(404);
    expect((await call('DELETE', `/watches/${id}`, { user: 'beto' })).status).toBe(404);
  });

  it('PATCH cambia el estado y el objetivo; rechaza un cuerpo vacío o un estado que no puede pedir el usuario', async () => {
    const created = await call('POST', '/watches', { body: BODY });
    const id = (created.body as { id: string }).id;

    const paused = await call('PATCH', `/watches/${id}`, { body: { estado: 'pausada', precioObjetivo: 2 } });
    expect(paused.status).toBe(200);
    expect(paused.body).toMatchObject({ estado: 'pausada', precioObjetivo: 2 });

    expect((await call('PATCH', `/watches/${id}`, { body: {} })).status).toBe(400);
    expect((await call('PATCH', `/watches/${id}`, { body: { estado: 'alcanzada' } })).status).toBe(400);
  });

  it('DELETE responde 204 sin cuerpo y después el recurso ya no existe', async () => {
    const created = await call('POST', '/watches', { body: BODY });
    const id = (created.body as { id: string }).id;

    const res = await call('DELETE', `/watches/${id}`);

    expect(res.status).toBe(204);
    expect(res.body).toBeUndefined();
    expect((await call('GET', `/watches/${id}`)).status).toBe(404);
  });

  it('un método no permitido responde 405 con el encabezado Allow; una ruta inexistente, 404', async () => {
    const wrongMethod = await call('PUT', '/watches');
    expect(wrongMethod.status).toBe(405);
    expect(wrongMethod.headers?.Allow).toBe('POST, GET');

    expect((await call('GET', '/nada')).status).toBe(404);
  });

  it('un error inesperado responde 500 genérico sin filtrar el detalle', async () => {
    const boom = new FakeWatchRepository();
    boom.ensureUser = async () => {
      throw new Error('password=secreta host=db.interno');
    };
    const broken = buildRouter(
      { watches: new WatchController(new WatchService(boom)) },
      async () => ({ clerkId: 'ana' })
    );

    const res = await broken.dispatch({ method: 'GET', path: '/watches', query: {}, headers: {}, body: undefined });

    expect(res.status).toBe(500);
    expect(JSON.stringify(res.body)).not.toContain('secreta');
  });
});
