import { ok, type HttpResult } from '../http/respond.js';
import { isDispatchEnabled } from './bus.js';
import type { EventDispatcher } from './dispatcher.js';
import type { EventQueue } from './types.js';

/**
 * Endpoints internos (protegidos con CRON_SECRET). Los llama el cron, un webhook de la base o a mano.
 * Con EVENTS_DISPATCH apagado no tocan la base: así siguen siendo inofensivos aunque la migración 006
 * todavía no esté aplicada.
 */
export class EventController {
  constructor(
    private readonly dispatcher: EventDispatcher,
    private readonly queue: EventQueue,
    private readonly enabled: () => boolean = isDispatchEnabled
  ) {}

  dispatch = async (): Promise<HttpResult> => {
    if (!this.enabled()) {
      return ok({ activo: false });
    }
    return ok({ activo: true, ...(await this.dispatcher.run()) });
  };

  stats = async (): Promise<HttpResult> => {
    if (!this.enabled()) {
      return ok({ activo: false });
    }
    return ok({ activo: true, ...(await this.queue.stats()) });
  };
}
