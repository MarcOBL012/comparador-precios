import type { ProductInput, WatchStatus } from '../watches/types.js';

export type AlertTypeCode = 'objetivo_alcanzado' | 'minimo_historico';

/** Un precio visto en una tienda. `fuente` coincide con price_sources.code. */
export interface Offer {
  tienda: string;
  titulo: string;
  precio: number;
  url: string | null;
  fuente: 'directa' | 'shopping';
}

/** Dónde se consiguen las ofertas. En producción busca en las tiendas reales; en pruebas se reemplaza. */
export interface PriceProvider {
  fetchOffers(product: ProductInput): Promise<Offer[]>;
}

export interface HistoryStats {
  minimo: number | null;
  observaciones: number;
  mediana: number | null;
}

export interface Decision {
  /** Ofertas que se guardan (se descartan las atípicas). */
  keep: Offer[];
  descartadas: number;
  mejor: Offer | null;
  alertas: AlertTypeCode[];
  nuevoEstado: 'alcanzada' | null;
}

export interface WatchRef {
  id: string;
  userId: string;
  productId: string;
  precioObjetivo: number | null;
  estado: WatchStatus;
}

export interface AlertRecord {
  id: number;
  tipoCodigo: AlertTypeCode;
  tipoNombre: string;
  seguimientoId: string;
  producto: string;
  tienda: string;
  precio: number;
  precioObjetivo: number | null;
  creadaEn: string;
  leidaEn: string | null;
}

export interface CheckOutcome {
  decision: Decision;
  alertas: AlertRecord[];
  estado: WatchStatus;
}

export interface HistoryPoint {
  observadoEn: string;
  tienda: string;
  precio: number;
}

export interface PriceRepository {
  /**
   * Guarda una revisión de forma atómica: ofertas, alertas, estado, última revisión y eventos de
   * dominio, todo en una transacción. `decide` (reglas puras) recibe el historial ANTERIOR a esta revisión.
   */
  recordCheck(watch: WatchRef, offers: Offer[], decide: (stats: HistoryStats) => Decision): Promise<CheckOutcome>;
  history(userId: string, watchId: string, since: Date): Promise<HistoryPoint[] | null>;
  listAlerts(
    userId: string,
    query: { unread: boolean; limit: number; offset: number }
  ): Promise<{ items: AlertRecord[]; total: number; sinLeer: number }>;
  markAlert(userId: string, alertId: number, read: boolean): Promise<AlertRecord | null>;
  /** Seguimientos activos sin revisar desde `olderThan` (o nunca), los más atrasados primero. */
  dueWatches(olderThan: Date, limit: number): Promise<Array<{ id: string; userId: string }>>;
}
