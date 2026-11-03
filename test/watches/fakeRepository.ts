import { ConflictError } from '../../lib/errors';
import type {
  ProductInput,
  Watch,
  WatchListQuery,
  WatchPatch,
  WatchRepository,
  WatchStatus,
} from '../../lib/watches/types';

/** Repositorio en memoria: permite probar reglas y HTTP sin base de datos. No es un dato simulado de la API. */
export class FakeWatchRepository implements WatchRepository {
  users = new Map<string, string>();
  products = new Map<string, string>();
  private originals = new Map<string, ProductInput>();
  watches: Array<Watch & { userId: string }> = [];
  private seq = 0;

  /** Para forzar el precio que "ve" el seguimiento en los tests de reactivación. */
  setBestPrice(id: string, price: number | null): void {
    const watch = this.watches.find((w) => w.id === id)!;
    watch.resumenPrecios = { ...watch.resumenPrecios, mejorPrecio: price };
  }

  forceStatus(id: string, status: WatchStatus): void {
    this.watches.find((w) => w.id === id)!.estado = status;
  }

  async ensureUser(clerkId: string): Promise<string> {
    if (!this.users.has(clerkId)) {
      this.users.set(clerkId, `user-${this.users.size + 1}`);
    }
    return this.users.get(clerkId)!;
  }

  async upsertProduct(input: ProductInput): Promise<string> {
    const key = [input.marca, input.nombre, input.presentacion].join('|').toLowerCase();
    if (!this.products.has(key)) {
      const id = `product-${this.products.size + 1}`;
      this.products.set(key, id);
      this.originals.set(id, input); // como la base real (citext): compara sin mayúsculas, conserva el primer texto
    }
    return this.products.get(key)!;
  }

  async insertWatch(userId: string, productId: string, precioObjetivo: number | null): Promise<string> {
    if (this.watches.some((w) => w.userId === userId && w.producto.id === productId)) {
      throw new ConflictError('Ya sigues este producto.', 'ya_seguido');
    }
    const id = `00000000-0000-4000-8000-${String(++this.seq).padStart(12, '0')}`;
    const { marca, nombre, presentacion, categoria, tipo } = this.originals.get(productId)!;
    this.watches.push({
      userId,
      id,
      producto: { id: productId, marca, nombre, presentacion, categoria, tipo },
      precioObjetivo,
      estado: 'activa',
      ultimaRevision: null,
      creadoEn: new Date(2026, 10, 3).toISOString(),
      actualizadoEn: new Date(2026, 10, 3).toISOString(),
      resumenPrecios: { mejorPrecio: null, tienda: null, minimoHistorico: null, observaciones: 0 },
    });
    return id;
  }

  async countActive(userId: string): Promise<number> {
    return this.watches.filter((w) => w.userId === userId && w.estado === 'activa').length;
  }

  async list(userId: string, query: WatchListQuery) {
    const mine = this.watches.filter((w) => w.userId === userId && (!query.estado || w.estado === query.estado));
    return { items: mine.slice(query.offset, query.offset + query.limit), total: mine.length };
  }

  async find(userId: string, id: string) {
    return this.watches.find((w) => w.userId === userId && w.id === id) ?? null;
  }

  async update(userId: string, id: string, patch: WatchPatch): Promise<boolean> {
    const watch = this.watches.find((w) => w.userId === userId && w.id === id);
    if (!watch) {
      return false;
    }
    if (patch.precioObjetivo !== undefined) watch.precioObjetivo = patch.precioObjetivo;
    if (patch.estado !== undefined) watch.estado = patch.estado;
    return true;
  }

  async remove(userId: string, id: string): Promise<boolean> {
    const before = this.watches.length;
    this.watches = this.watches.filter((w) => !(w.userId === userId && w.id === id));
    return this.watches.length < before;
  }
}
