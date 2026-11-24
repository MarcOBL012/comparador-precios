import { randomUUID } from 'node:crypto';
import type { Sql } from '../db/client.js';
import type { PriceRepository } from '../prices/types.js';
import type { EventBus } from './bus.js';

/** Un seguimiento se vuelve a pedir como máximo una vez por ventana de 6 horas (igual que «vence a las 6 h»). */
const WINDOW_MS = 6 * 3600 * 1000;

export const checkDedupeKey = (watchId: string, now: Date): string => `check:${watchId}:${Math.floor(now.getTime() / WINDOW_MS)}`;

/**
 * Productor de revisiones programadas: en vez de revisar en un bucle (lo que limitaba el cron a 5
 * seguimientos por los 60 s de la función), publica un `watch.check_requested` por cada seguimiento
 * vencido. Cada revisión corre luego como una entrega independiente, con sus propios reintentos.
 */
export class CheckScheduler {
  constructor(
    private readonly sql: Sql,
    private readonly prices: Pick<PriceRepository, 'dueWatches'>,
    private readonly bus: EventBus
  ) {}

  /**
   * Devuelve cuántas revisiones nuevas se pidieron (las repetidas en la misma ventana se ignoran) y la
   * correlación de la corrida: todo lo que estas revisiones provoquen (watch.checked, alert.created...)
   * la hereda, así se puede preguntar qué resultó de ESTA corrida sin comparar relojes.
   */
  async schedule(olderThan: Date, limit: number, now: Date): Promise<{ programados: number; correlationId: string }> {
    const due = await this.prices.dueWatches(olderThan, limit);
    const correlationId = randomUUID(); // todas las revisiones de esta corrida comparten la misma cadena
    let scheduled = 0;
    for (const watch of due) {
      const id = await this.sql.begin((tx) =>
        this.bus.publish(tx, {
          type: 'watch.check_requested',
          aggregateType: 'watch',
          aggregateId: watch.id,
          payload: { watchId: watch.id, userId: watch.userId, motivo: 'programada' },
          correlationId,
          actor: 'system',
          dedupeKey: checkDedupeKey(watch.id, now),
        })
      );
      if (id !== null) scheduled += 1;
    }
    return { programados: scheduled, correlationId };
  }
}
