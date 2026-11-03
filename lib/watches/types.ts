import type { Categoria } from '../productIdentification.js';

export type WatchStatus = 'activa' | 'pausada' | 'alcanzada';

export interface ProductInput {
  marca: string;
  nombre: string;
  presentacion: string;
  categoria: Categoria;
  tipo: string;
}

export interface PriceSummary {
  /** Mejor precio entre las tiendas, según la última observación de cada una. */
  mejorPrecio: number | null;
  tienda: string | null;
  minimoHistorico: number | null;
  observaciones: number;
}

export interface Watch {
  id: string;
  producto: ProductInput & { id: string };
  precioObjetivo: number | null;
  estado: WatchStatus;
  ultimaRevision: string | null;
  creadoEn: string;
  actualizadoEn: string;
  resumenPrecios: PriceSummary;
}

export interface WatchListQuery {
  estado?: WatchStatus;
  limit: number;
  offset: number;
}

export interface WatchPatch {
  /** `undefined` = no tocar; `null` = quitar el objetivo (solo avisar mínimos históricos). */
  precioObjetivo?: number | null;
  estado?: 'activa' | 'pausada';
}

/** Acceso a datos: la única capa que conoce SQL. El servicio depende de esta interfaz, no de Postgres. */
export interface WatchRepository {
  /** Crea la fila del usuario la primera vez que aparece y devuelve su id interno. */
  ensureUser(clerkId: string): Promise<string>;
  upsertProduct(input: ProductInput): Promise<string>;
  /** Lanza ConflictError('ya_seguido') si la persona ya sigue ese producto. */
  insertWatch(userId: string, productId: string, precioObjetivo: number | null): Promise<string>;
  countActive(userId: string): Promise<number>;
  list(userId: string, query: WatchListQuery): Promise<{ items: Watch[]; total: number }>;
  find(userId: string, id: string): Promise<Watch | null>;
  /** false = no existe (o es de otra persona). Lanza ConflictError('transicion_invalida') si la base la rechaza. */
  update(userId: string, id: string, patch: WatchPatch): Promise<boolean>;
  remove(userId: string, id: string): Promise<boolean>;
}
