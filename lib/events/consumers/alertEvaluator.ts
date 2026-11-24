import type { TransactionSql } from 'postgres';
import type { Sql } from '../../db/client.js';
import { decide } from '../../prices/alertRules.js';
import type { AlertTypeCode, Offer } from '../../prices/types.js';
import type { WatchStatus } from '../../watches/types.js';
import type { EventBus } from '../bus.js';
import { validatePayload, type EventType } from '../catalog.js';
import { PermanentError } from '../retryPolicy.js';
import type { Consumer, StoredEvent } from '../types.js';

export const ALERT_EVALUATOR = 'alert_evaluator';

interface WatchState {
  status_code: WatchStatus;
  target: number | null;
}

/**
 * Decide las alertas a partir de los eventos, con las MISMAS reglas puras de antes (`alertRules.decide`):
 *
 *  · `watch.checked`: la mejor oferta de una revisión se evalúa contra TODOS los seguimientos activos
 *    de ese producto, no solo el que se revisó. El historial de precios es del producto (migración 002),
 *    así que si Ana revisa, Luis también recibe su alerta sin haber hecho nada.
 *  · `watch.target_changed`: si el nuevo objetivo ya lo cumple el mejor precio actual, avisa al instante.
 *
 * Es idempotente: `unique (observation_id, watch_id, alert_type_code)` impide duplicar una alerta y
 * el cambio de estado solo ocurre desde «activa», así que reprocesar un evento no cambia nada.
 */
export class AlertEvaluator implements Consumer {
  readonly name = ALERT_EVALUATOR;
  readonly handles: readonly EventType[] = ['watch.checked', 'watch.target_changed'];

  constructor(
    private readonly sql: Sql,
    private readonly bus: EventBus
  ) {}

  async handle(event: StoredEvent): Promise<void> {
    if (event.type === 'watch.checked') return this.onChecked(event);
    if (event.type === 'watch.target_changed') return this.onTargetChanged(event);
  }

  private async onChecked(event: StoredEvent): Promise<void> {
    const p = validatePayload('watch.checked', event.payload);
    if (!p.productId || !p.statsPrevias) {
      throw new PermanentError('watch.checked sin productId ni statsPrevias: es un evento del modo anterior y no se puede evaluar.');
    }
    if (!p.mejor) {
      return; // la revisión no encontró precios: no hay nada que comparar
    }
    const { productId, mejor, statsPrevias } = p;
    const offer: Offer = { tienda: mejor.tienda, titulo: mejor.tienda, precio: mejor.precio, url: null, fuente: 'directa' };

    const watches = await this.sql<{ id: string }[]>`
      select id from public.watches where product_id = ${productId} and status_code <> 'pausada' order by id`;
    for (const { id } of watches) {
      // Una transacción por seguimiento: la alerta, el cambio de estado y su evento se guardan juntos.
      await this.sql.begin(async (tx) => {
        const current = await this.lock(tx, id);
        if (!current || current.status_code === 'pausada') return;
        const decision = decide({ objetivo: current.target, estado: current.status_code, stats: statsPrevias, offers: [offer] });
        if (decision.alertas.length === 0 && decision.nuevoEstado === null) return;
        await this.createAlerts(tx, id, mejor.observationId, decision.alertas, decision.nuevoEstado, event);
      });
    }
  }

  private async onTargetChanged(event: StoredEvent): Promise<void> {
    const { watchId, productId, nuevo } = validatePayload('watch.target_changed', event.payload);
    if (nuevo === null) {
      return; // quitó el objetivo: solo se avisarán mínimos históricos en la próxima revisión
    }
    await this.sql.begin(async (tx) => {
      const current = await this.lock(tx, watchId);
      // Se usa el objetivo vigente en la base (no el del evento): si lo cambiaron otra vez, manda el último.
      if (!current || current.status_code !== 'activa' || current.target === null) return;
      // Mejor precio actual: la última observación de cada tienda, la más barata.
      const [best] = await tx<{ id: string; precio: number }[]>`
        select o.id::text as id, o.precio::float8 as precio
        from public.store_listings l
        join lateral (
          select id, precio from public.price_observations
          where listing_id = l.id order by observed_at desc, id desc limit 1
        ) o on true
        where l.product_id = ${productId}
        order by o.precio asc, o.id
        limit 1`;
      if (!best || best.precio > current.target) return;
      await this.createAlerts(tx, watchId, Number(best.id), ['objetivo_alcanzado'], 'alcanzada', event);
    });
  }

  private async lock(tx: TransactionSql, watchId: string): Promise<WatchState | undefined> {
    const [row] = await tx<WatchState[]>`
      select status_code, target_price::float8 as target from public.watches where id = ${watchId} for update`;
    return row;
  }

  private async createAlerts(
    tx: TransactionSql,
    watchId: string,
    observationId: number,
    tipos: AlertTypeCode[],
    nuevoEstado: 'alcanzada' | null,
    cause: StoredEvent
  ): Promise<void> {
    const created: number[] = [];
    for (const tipo of tipos) {
      const [row] = await tx<{ id: string }[]>`
        insert into public.alerts (watch_id, observation_id, alert_type_code)
        values (${watchId}, ${observationId}, ${tipo})
        on conflict (observation_id, watch_id, alert_type_code) do nothing
        returning id::text as id`;
      if (row) created.push(Number(row.id));
    }
    if (nuevoEstado) {
      await tx`update public.watches set status_code = ${nuevoEstado} where id = ${watchId} and status_code = 'activa'`;
    }
    // Cada alerta nueva anuncia su propio evento, encadenado al que la causó (misma correlación).
    for (const alertId of created) {
      await this.bus.publish(tx, {
        type: 'alert.created',
        aggregateType: 'alert',
        aggregateId: String(alertId),
        payload: { alertId, watchId },
        correlationId: cause.correlationId,
        causationId: cause.id,
        actor: 'system',
      });
    }
  }
}
