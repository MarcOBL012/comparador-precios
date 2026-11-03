import { ConflictError, NotFoundError, RateLimitedError, UpstreamError } from '../errors.js';
import type { WatchRepository, Watch, WatchStatus } from '../watches/types.js';
import { alertMessage, decide, summarizeHistory, type HistorySummary } from './alertRules.js';
import type { AlertRecord, HistoryPoint, PriceProvider, PriceRepository } from './types.js';

/** R3: una revisión manual cada 15 minutos por seguimiento (protege los créditos de las tiendas y la IA). */
export const MIN_SECONDS_BETWEEN_CHECKS = 15 * 60;
/** El cron revisa lo que lleva más de 6 horas sin revisarse. */
export const DUE_AFTER_HOURS = 6;
/** Tope por corrida del cron: cada revisión consulta varias tiendas y la función dura como máximo 60 s. */
export const CRON_BATCH_SIZE = 5;

export interface AlertView {
  id: number;
  tipo: { codigo: string; nombre: string };
  mensaje: string;
  seguimientoId: string;
  producto: string;
  tienda: string;
  precio: number;
  creadaEn: string;
  leida: boolean;
}

export function toAlertView(a: AlertRecord): AlertView {
  return {
    id: a.id,
    tipo: { codigo: a.tipoCodigo, nombre: a.tipoNombre },
    mensaje: alertMessage(a),
    seguimientoId: a.seguimientoId,
    producto: a.producto,
    tienda: a.tienda,
    precio: a.precio,
    creadaEn: a.creadaEn,
    leida: a.leidaEn !== null,
  };
}

export interface CheckResult {
  seguimiento: Watch;
  ofertasGuardadas: number;
  ofertasDescartadas: number;
  alertasNuevas: AlertView[];
}

export interface HistoryResult {
  dias: number;
  puntos: HistoryPoint[];
  estadisticas: HistorySummary;
}

export class PriceService {
  constructor(
    private readonly watches: WatchRepository,
    private readonly prices: PriceRepository,
    private readonly provider: PriceProvider,
    private readonly now: () => Date = () => new Date()
  ) {}

  async check(clerkId: string, watchId: string): Promise<CheckResult> {
    const userId = await this.watches.ensureUser(clerkId);
    const watch = await this.watches.find(userId, watchId);
    if (!watch) {
      throw new NotFoundError('No encontramos ese seguimiento.');
    }
    if (watch.estado === 'pausada') {
      throw new ConflictError('Este seguimiento está pausado. Reactívalo para revisar su precio.', 'seguimiento_pausado');
    }
    this.enforceRateLimit(watch);
    return this.execute(userId, watch);
  }

  private enforceRateLimit(watch: Watch): void {
    if (!watch.ultimaRevision) {
      return;
    }
    const elapsed = (this.now().getTime() - new Date(watch.ultimaRevision).getTime()) / 1000;
    if (elapsed < MIN_SECONDS_BETWEEN_CHECKS) {
      const wait = Math.ceil(MIN_SECONDS_BETWEEN_CHECKS - elapsed);
      throw new RateLimitedError(
        `Revisaste este producto hace poco. Vuelve a intentarlo en ${Math.ceil(wait / 60)} min.`,
        wait
      );
    }
  }

  private async execute(userId: string, watch: Watch): Promise<CheckResult> {
    let offers;
    try {
      offers = await this.provider.fetchOffers(watch.producto);
    } catch (err) {
      throw err instanceof UpstreamError ? err : new UpstreamError('No pudimos consultar las tiendas ahora.', err);
    }
    const outcome = await this.prices.recordCheck(
      { id: watch.id, userId, productId: watch.producto.id, precioObjetivo: watch.precioObjetivo, estado: watch.estado },
      offers,
      (stats) => decide({ objetivo: watch.precioObjetivo, estado: watch.estado, stats, offers })
    );
    const updated = await this.watches.find(userId, watch.id);
    return {
      seguimiento: updated ?? { ...watch, estado: outcome.estado as WatchStatus },
      ofertasGuardadas: outcome.decision.keep.length,
      ofertasDescartadas: outcome.decision.descartadas,
      alertasNuevas: outcome.alertas.map(toAlertView),
    };
  }

  async history(clerkId: string, watchId: string, days: number): Promise<HistoryResult> {
    const userId = await this.watches.ensureUser(clerkId);
    const since = new Date(this.now().getTime() - days * 24 * 3600 * 1000);
    const puntos = await this.prices.history(userId, watchId, since);
    if (puntos === null) {
      throw new NotFoundError('No encontramos ese seguimiento.');
    }
    return { dias: days, puntos, estadisticas: summarizeHistory(puntos) };
  }

  async listAlerts(clerkId: string, query: { unread: boolean; limit: number; offset: number }) {
    const result = await this.prices.listAlerts(await this.watches.ensureUser(clerkId), query);
    return { items: result.items.map(toAlertView), total: result.total, sinLeer: result.sinLeer };
  }

  async markAlert(clerkId: string, alertId: number, read: boolean): Promise<AlertView> {
    const alert = await this.prices.markAlert(await this.watches.ensureUser(clerkId), alertId, read);
    if (!alert) {
      throw new NotFoundError('No encontramos esa alerta.');
    }
    return toAlertView(alert);
  }

  /** Lo llama el cron: revisa un lote de seguimientos atrasados; un fallo no detiene a los demás. */
  async checkDue(batch = CRON_BATCH_SIZE): Promise<{ revisados: number; alertas: number; errores: number }> {
    const olderThan = new Date(this.now().getTime() - DUE_AFTER_HOURS * 3600 * 1000);
    const due = await this.prices.dueWatches(olderThan, batch);
    let revisados = 0;
    let alertas = 0;
    let errores = 0;
    for (const { id, userId } of due) {
      try {
        const watch = await this.watches.find(userId, id);
        if (!watch) continue;
        const result = await this.execute(userId, watch);
        revisados += 1;
        alertas += result.alertasNuevas.length;
      } catch (err) {
        errores += 1;
        console.error(`Revisión automática falló para ${id}:`, err);
      }
    }
    return { revisados, alertas, errores };
  }
}
