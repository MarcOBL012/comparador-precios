import { beforeEach, describe, expect, it } from 'vitest';
import { ConflictError, NotFoundError, UnprocessableError } from '../../lib/errors';
import { USER_TRANSITIONS, WatchService, cleanProduct } from '../../lib/watches/service';
import { FakeWatchRepository } from './fakeRepository';

const FREE_TEA = { marca: 'Free Tea', nombre: 'Frutos Rojos', presentacion: '500ml', categoria: 'bebidas', tipo: 'té' } as const;
const OTRO = { marca: 'Gloria', nombre: 'Leche evaporada', presentacion: '400g', categoria: 'abarrotes', tipo: 'leche evaporada' } as const;

describe('WatchService', () => {
  let repo: FakeWatchRepository;
  let service: WatchService;

  beforeEach(() => {
    repo = new FakeWatchRepository();
    service = new WatchService(repo, 3);
  });

  it('crea un seguimiento activo y lo devuelve con su producto', async () => {
    const watch = await service.create('user_a', FREE_TEA, 2.5);

    expect(watch.estado).toBe('activa');
    expect(watch.precioObjetivo).toBe(2.5);
    expect(watch.producto.nombre).toBe('Frutos Rojos');
  });

  it('R1: rechaza seguir dos veces el mismo producto (409 ya_seguido), aunque cambien mayúsculas y espacios', async () => {
    await service.create('user_a', FREE_TEA, null);

    await expect(
      service.create('user_a', { ...FREE_TEA, nombre: '  frutos   ROJOS ', presentacion: '500ML' }, null)
    ).rejects.toMatchObject({ codigo: 'ya_seguido' });
  });

  it('dos personas pueden seguir el mismo producto', async () => {
    await service.create('user_a', FREE_TEA, null);

    await expect(service.create('user_b', FREE_TEA, null)).resolves.toBeDefined();
  });

  it('R2: no deja pasar del máximo de seguimientos activos (409 limite_seguimientos)', async () => {
    for (const n of ['A', 'B', 'C']) {
      await service.create('user_a', { ...FREE_TEA, nombre: `Producto ${n}` }, null);
    }

    await expect(service.create('user_a', OTRO, null)).rejects.toMatchObject({ codigo: 'limite_seguimientos' });
  });

  it('un seguimiento pausado no cuenta para el límite', async () => {
    const first = await service.create('user_a', FREE_TEA, null);
    await service.create('user_a', { ...FREE_TEA, nombre: 'B' }, null);
    await service.create('user_a', { ...FREE_TEA, nombre: 'C' }, null);
    await service.update('user_a', first.id, { estado: 'pausada' });

    await expect(service.create('user_a', OTRO, null)).resolves.toBeDefined();
  });

  it('R7: pausar y reactivar son transiciones válidas', async () => {
    const watch = await service.create('user_a', FREE_TEA, null);

    expect((await service.update('user_a', watch.id, { estado: 'pausada' })).estado).toBe('pausada');
    expect((await service.update('user_a', watch.id, { estado: 'activa' })).estado).toBe('activa');
  });

  it('cambiar solo el objetivo no toca el estado', async () => {
    const watch = await service.create('user_a', FREE_TEA, 3);

    const updated = await service.update('user_a', watch.id, { precioObjetivo: 2 });

    expect(updated.precioObjetivo).toBe(2);
    expect(updated.estado).toBe('activa');
  });

  it('permite quitar el objetivo con null', async () => {
    const watch = await service.create('user_a', FREE_TEA, 3);

    expect((await service.update('user_a', watch.id, { precioObjetivo: null })).precioObjetivo).toBeNull();
  });

  it('reactivar uno alcanzado exige un objetivo menor al precio actual (422)', async () => {
    const watch = await service.create('user_a', FREE_TEA, 2.5);
    repo.forceStatus(watch.id, 'alcanzada');
    repo.setBestPrice(watch.id, 2.0);

    await expect(service.update('user_a', watch.id, { estado: 'activa' })).rejects.toBeInstanceOf(UnprocessableError);
    await expect(
      service.update('user_a', watch.id, { estado: 'activa', precioObjetivo: 2.0 })
    ).rejects.toBeInstanceOf(UnprocessableError);
    await expect(
      service.update('user_a', watch.id, { estado: 'activa', precioObjetivo: 1.8 })
    ).resolves.toMatchObject({ estado: 'activa', precioObjetivo: 1.8 });
  });

  it('un seguimiento ajeno responde como no encontrado, no como prohibido', async () => {
    const watch = await service.create('user_a', FREE_TEA, null);

    await expect(service.get('user_b', watch.id)).rejects.toBeInstanceOf(NotFoundError);
    await expect(service.update('user_b', watch.id, { estado: 'pausada' })).rejects.toBeInstanceOf(NotFoundError);
    await expect(service.remove('user_b', watch.id)).rejects.toBeInstanceOf(NotFoundError);
  });

  it('elimina un seguimiento y luego ya no existe', async () => {
    const watch = await service.create('user_a', FREE_TEA, null);

    await service.remove('user_a', watch.id);

    await expect(service.get('user_a', watch.id)).rejects.toBeInstanceOf(NotFoundError);
  });

  it('lista solo los seguimientos de la persona, con filtro por estado y paginación', async () => {
    const a = await service.create('user_a', FREE_TEA, null);
    await service.create('user_a', { ...FREE_TEA, nombre: 'B' }, null);
    await service.create('user_b', OTRO, null);
    await service.update('user_a', a.id, { estado: 'pausada' });

    expect((await service.list('user_a', { limit: 20, offset: 0 })).total).toBe(2);
    expect((await service.list('user_a', { limit: 20, offset: 0, estado: 'pausada' })).items).toHaveLength(1);
    expect((await service.list('user_a', { limit: 1, offset: 1 })).items).toHaveLength(1);
  });

  it('las transiciones de usuario coinciden con las permitidas en la base de datos (migración 001)', () => {
    // Fuente: INSERT en watch_status_transitions con actor = 'usuario'.
    expect(USER_TRANSITIONS).toEqual({
      activa: ['pausada'],
      pausada: ['activa'],
      alcanzada: ['activa', 'pausada'],
    });
  });

  it('cleanProduct colapsa espacios y recorta', () => {
    expect(cleanProduct({ ...FREE_TEA, nombre: '  Frutos \n  Rojos ' }).nombre).toBe('Frutos Rojos');
  });

  it('un 409 de transición inválida no deja el estado cambiado', async () => {
    const watch = await service.create('user_a', FREE_TEA, null);

    await expect(service.update('user_a', watch.id, { estado: 'alcanzada' as never })).rejects.toBeInstanceOf(ConflictError);
    expect((await service.get('user_a', watch.id)).estado).toBe('activa');
  });
});
