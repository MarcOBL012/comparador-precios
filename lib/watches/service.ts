import { ConflictError, NotFoundError, UnprocessableError } from '../errors.js';
import type { ProductInput, Watch, WatchListQuery, WatchPatch, WatchRepository, WatchStatus } from './types.js';

/** R2: tope de seguimientos activos por persona (cada uno cuesta consultas a tiendas e IA). */
export const MAX_ACTIVE_WATCHES = 20;

/**
 * Transiciones que la PERSONA puede pedir. Espejo de watch_status_transitions (actor 'usuario');
 * activa → alcanzada solo la hace el sistema al revisar precios. Un test compara ambas listas.
 */
export const USER_TRANSITIONS: Record<WatchStatus, WatchStatus[]> = {
  activa: ['pausada'],
  pausada: ['activa'],
  alcanzada: ['activa', 'pausada'],
};

const collapse = (text: string) => text.replace(/\s+/g, ' ').trim();

export function cleanProduct(input: ProductInput): ProductInput {
  return {
    marca: collapse(input.marca),
    nombre: collapse(input.nombre),
    presentacion: collapse(input.presentacion),
    categoria: input.categoria,
    tipo: collapse(input.tipo),
  };
}

const soles = (n: number) => `S/ ${n.toFixed(2)}`;

export class WatchService {
  constructor(
    private readonly repo: WatchRepository,
    private readonly maxActive = MAX_ACTIVE_WATCHES
  ) {}

  async create(clerkId: string, producto: ProductInput, precioObjetivo: number | null): Promise<Watch> {
    const userId = await this.repo.ensureUser(clerkId);
    if ((await this.repo.countActive(userId)) >= this.maxActive) {
      throw new ConflictError(
        `Llegaste al máximo de ${this.maxActive} seguimientos activos. Pausa o elimina uno para agregar otro.`,
        'limite_seguimientos'
      );
    }
    const productId = await this.repo.upsertProduct(cleanProduct(producto));
    const id = await this.repo.insertWatch(userId, productId, precioObjetivo);
    return this.get(clerkId, id);
  }

  async list(clerkId: string, query: WatchListQuery): Promise<{ items: Watch[]; total: number }> {
    return this.repo.list(await this.repo.ensureUser(clerkId), query);
  }

  async get(clerkId: string, id: string): Promise<Watch> {
    const watch = await this.repo.find(await this.repo.ensureUser(clerkId), id);
    if (!watch) {
      throw new NotFoundError('No encontramos ese seguimiento.');
    }
    return watch;
  }

  async update(clerkId: string, id: string, patch: WatchPatch): Promise<Watch> {
    const userId = await this.repo.ensureUser(clerkId);
    const current = await this.repo.find(userId, id);
    if (!current) {
      throw new NotFoundError('No encontramos ese seguimiento.');
    }

    if (patch.estado !== undefined && patch.estado !== current.estado) {
      if (!USER_TRANSITIONS[current.estado].includes(patch.estado)) {
        throw new ConflictError(
          `Un seguimiento ${current.estado} no puede pasar a ${patch.estado}.`,
          'transicion_invalida'
        );
      }
      if (current.estado === 'alcanzada' && patch.estado === 'activa') {
        this.requireNewTargetBelowCurrentPrice(current, patch);
      }
    }

    await this.repo.update(userId, id, patch);
    return this.get(clerkId, id);
  }

  /**
   * Reactivar uno que ya alcanzó su objetivo con el mismo objetivo lo volvería a disparar en la
   * próxima revisión: se exige un objetivo nuevo, más bajo que el mejor precio de hoy.
   */
  private requireNewTargetBelowCurrentPrice(current: Watch, patch: WatchPatch): void {
    const target = patch.precioObjetivo !== undefined ? patch.precioObjetivo : current.precioObjetivo;
    const best = current.resumenPrecios.mejorPrecio;
    if (target !== null && best !== null && target >= best) {
      throw new UnprocessableError('Para reactivarlo, el nuevo objetivo debe ser menor al precio actual.', [
        { campo: 'precioObjetivo', mensaje: `Debe ser menor a ${soles(best)}` },
      ]);
    }
  }

  async remove(clerkId: string, id: string): Promise<void> {
    const userId = await this.repo.ensureUser(clerkId);
    if (!(await this.repo.remove(userId, id))) {
      throw new NotFoundError('No encontramos ese seguimiento.');
    }
  }
}
