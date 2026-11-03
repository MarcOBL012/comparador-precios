import type { WatchStatus } from '../watches/types.js';
import type { AlertRecord, AlertTypeCode, Decision, HistoryPoint, HistoryStats, Offer } from './types.js';

/** R6: un precio por debajo de esta fracción de la mediana histórica casi seguro es un error de coincidencia. */
export const OUTLIER_RATIO = 0.4;
/** R5/R6: con menos observaciones que esto no hay historial confiable para comparar. */
export const MIN_OBSERVATIONS = 3;

const round2 = (n: number) => Math.round(n * 100) / 100;

/**
 * Reglas de negocio de una revisión, como función pura (sin base de datos ni red):
 *  R6  descarta precios atípicos (< 40 % de la mediana, con al menos 3 observaciones previas);
 *  R4  objetivo_alcanzado: el mejor precio llegó al objetivo y el seguimiento estaba activo;
 *  R5  minimo_historico: el mejor precio es menor al mínimo anterior (con al menos 3 observaciones previas).
 */
export function decide(input: {
  objetivo: number | null;
  estado: WatchStatus;
  stats: HistoryStats;
  offers: Offer[];
}): Decision {
  const { objetivo, estado, stats, offers } = input;
  const hasHistory = stats.observaciones >= MIN_OBSERVATIONS;
  const keep =
    hasHistory && stats.mediana !== null
      ? offers.filter((o) => o.precio >= stats.mediana! * OUTLIER_RATIO)
      : offers;
  const descartadas = offers.length - keep.length;
  const mejor = keep.reduce<Offer | null>((best, o) => (best === null || o.precio < best.precio ? o : best), null);

  const alertas: AlertTypeCode[] = [];
  let nuevoEstado: Decision['nuevoEstado'] = null;
  if (mejor) {
    if (estado === 'activa' && objetivo !== null && mejor.precio <= objetivo) {
      alertas.push('objetivo_alcanzado');
      nuevoEstado = 'alcanzada';
    }
    if (hasHistory && stats.minimo !== null && mejor.precio < stats.minimo) {
      alertas.push('minimo_historico');
    }
  }
  return { keep, descartadas, mejor, alertas, nuevoEstado };
}

const soles = (n: number) => `S/ ${n.toFixed(2)}`;

/** El mensaje no se guarda: se deriva del tipo y de los datos de la observación (3FN). */
export function alertMessage(a: Pick<AlertRecord, 'tipoCodigo' | 'producto' | 'tienda' | 'precio' | 'precioObjetivo'>): string {
  if (a.tipoCodigo === 'objetivo_alcanzado') {
    const objetivo = a.precioObjetivo !== null ? ` (tu objetivo: ${soles(a.precioObjetivo)})` : '';
    return `${a.producto} bajó a ${soles(a.precio)} en ${a.tienda}${objetivo}.`;
  }
  return `${a.producto} está en su precio más bajo registrado: ${soles(a.precio)} en ${a.tienda}.`;
}

export type Tendencia = 'baja' | 'sube' | 'estable' | 'sin_datos';

export interface HistorySummary {
  minimo: number | null;
  maximo: number | null;
  promedio: number | null;
  cantidad: number;
  tendencia: Tendencia;
}

/** Cambio mínimo (en %) entre el primer y el último día para hablar de tendencia. */
const TREND_THRESHOLD = 0.02;

export function summarizeHistory(points: HistoryPoint[]): HistorySummary {
  if (points.length === 0) {
    return { minimo: null, maximo: null, promedio: null, cantidad: 0, tendencia: 'sin_datos' };
  }
  const prices = points.map((p) => p.precio);
  // Mejor precio de cada día: así una tienda cara un día no parece una "subida".
  const bestByDay = new Map<string, number>();
  for (const p of points) {
    const day = p.observadoEn.slice(0, 10);
    bestByDay.set(day, Math.min(bestByDay.get(day) ?? Infinity, p.precio));
  }
  const days = [...bestByDay.entries()].sort(([a], [b]) => a.localeCompare(b));
  let tendencia: Tendencia = 'sin_datos';
  if (days.length >= 2) {
    const change = (days[days.length - 1][1] - days[0][1]) / days[0][1];
    tendencia = change <= -TREND_THRESHOLD ? 'baja' : change >= TREND_THRESHOLD ? 'sube' : 'estable';
  }
  return {
    minimo: Math.min(...prices),
    maximo: Math.max(...prices),
    promedio: round2(prices.reduce((sum, p) => sum + p, 0) / prices.length),
    cantidad: prices.length,
    tendencia,
  };
}
