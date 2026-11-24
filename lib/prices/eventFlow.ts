import { randomUUID } from 'node:crypto';
import { ALERT_EVALUATOR } from '../events/consumers/alertEvaluator.js';
import { WATCH_CHECKER } from '../events/consumers/watchChecker.js';
import type { EventDispatcher } from '../events/dispatcher.js';
import type { CheckScheduler } from '../events/scheduler.js';
import type { Watch, WatchRepository } from '../watches/types.js';
import type { CheckRecorder } from './checkRecorder.js';
import { DUE_AFTER_HOURS, toAlertView, type CheckResult } from './service.js';
import type { PriceRepository } from './types.js';

/** Cuánto espera «Revisar ahora» a que se decidan las alertas de SU revisión (el resto sigue en segundo plano). */
export const SETTLE_BUDGET_MS = 8_000;
/** Seguimientos que el cron pide revisar por corrida; los que no alcancen quedan como entregas pendientes. */
export const CRON_SCHEDULE_LIMIT = 20;

/**
 * La revisión de precios por eventos. Reemplaza al modo anterior detrás de EVENTS_ALERTS=on y
 * conserva su contrato HTTP al pie de la letra (mismas respuestas y mismos códigos).
 */
export class EventCheckFlow {
  constructor(
    private readonly recorder: CheckRecorder,
    private readonly prices: PriceRepository,
    private readonly watches: WatchRepository,
    private readonly dispatcher: Pick<EventDispatcher, 'run'>,
    private readonly scheduler: CheckScheduler,
    private readonly now: () => Date = () => new Date()
  ) {}

  /** «Revisar ahora»: guarda, decide las alertas en línea (acotado) y responde igual que antes. */
  async check(userId: string, watch: Watch): Promise<CheckResult> {
    const correlationId = randomUUID();
    const outcome = await this.recorder.record(userId, watch, { correlationId, actor: `user:${userId}` });
    try {
      // Solo las alertas de ESTA revisión: no espera notificaciones ni nada más lento.
      await this.dispatcher.run({ correlationId, consumers: [ALERT_EVALUATOR], budgetMs: SETTLE_BUDGET_MS });
    } catch (err) {
      // Las alertas igual se decidirán en segundo plano; esta respuesta saldrá sin ellas.
      console.error('Despacho en línea de la revisión falló:', err);
    }
    const alertas = outcome.mejor ? await this.prices.alertsOfObservation(watch.id, outcome.mejor.observationId) : [];
    const updated = await this.watches.find(userId, watch.id);
    return {
      seguimiento: updated ?? watch,
      ofertasGuardadas: outcome.guardadas,
      ofertasDescartadas: outcome.descartadas,
      alertasNuevas: alertas.map(toAlertView),
    };
  }

  /**
   * Cron: programa las revisiones vencidas como eventos y las procesa. Devuelve el mismo resumen de
   * siempre (revisados, alertas, errores) más cuántas se programaron.
   */
  async checkDue(): Promise<{ revisados: number; alertas: number; errores: number; programados: number }> {
    const olderThan = new Date(this.now().getTime() - DUE_AFTER_HOURS * 3600 * 1000);
    const { programados, correlationId } = await this.scheduler.schedule(olderThan, CRON_SCHEDULE_LIMIT, this.now());
    const summary = await this.dispatcher.run({ consumers: [WATCH_CHECKER, ALERT_EVALUATOR] });
    return {
      revisados: summary.hechasPorConsumidor[WATCH_CHECKER] ?? 0,
      // Por correlación, no por hora: la app y la base de datos no comparten reloj.
      alertas: await this.prices.countAlertsOfRun(correlationId),
      errores: summary.reintentos + summary.muertas,
      programados,
    };
  }
}
