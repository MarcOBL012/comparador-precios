import type { CheckRecorder } from '../../prices/checkRecorder.js';
import type { WatchRepository } from '../../watches/types.js';
import { validatePayload, type EventType } from '../catalog.js';
import type { Consumer, StoredEvent } from '../types.js';

export const WATCH_CHECKER = 'watch_checker';

/**
 * Hace la revisión de precios que pidió el scheduler (`watch.check_requested`). Si las tiendas no
 * responden lanza un error transitorio y el despachador lo reintenta con espera creciente; antes,
 * una falla del cron simplemente se perdía hasta el día siguiente.
 */
export class WatchChecker implements Consumer {
  readonly name = WATCH_CHECKER;
  readonly handles: readonly EventType[] = ['watch.check_requested'];

  constructor(
    private readonly watches: WatchRepository,
    private readonly recorder: CheckRecorder
  ) {}

  async handle(event: StoredEvent): Promise<void> {
    const { watchId, userId } = validatePayload('watch.check_requested', event.payload);
    const watch = await this.watches.find(userId, watchId);
    // Lo pausaron, lo borraron o ya alcanzó su objetivo mientras esperaba: no hay nada que revisar.
    if (!watch || watch.estado !== 'activa') {
      return;
    }
    // Idempotencia: si ya se revisó después de pedirlo (esta entrega se repite tras un fallo al confirmar), no se repite.
    if (watch.ultimaRevision && new Date(watch.ultimaRevision).getTime() >= event.occurredAt.getTime()) {
      return;
    }
    await this.recorder.record(userId, watch, { correlationId: event.correlationId, causationId: event.id, actor: 'system' });
  }
}
