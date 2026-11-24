import { authenticate } from './auth.js';
import { buildDomain, isEventAlertsEnabled } from './compose.js';
import { getSql } from './db/client.js';
import { EventController } from './events/controller.js';
import type { EventDispatcher } from './events/dispatcher.js';
import { Router } from './http/router.js';
import { PriceController } from './prices/controller.js';
import { StoreSearchPriceProvider } from './prices/provider.js';
import { WatchController } from './watches/controller.js';

export interface AppControllers {
  watches: WatchController;
  /** Opcional solo para poder probar los seguimientos sin la parte de precios. */
  prices?: PriceController;
  /** Endpoints internos del despachador de eventos (cron / webhook / a mano). */
  events?: EventController;
}

/** Tabla de rutas de la API v1. Cada fila: método, ruta, quién puede llamarla y qué controlador la atiende. */
export function buildRouter(controllers: AppControllers, auth = authenticate): Router {
  const router = new Router({ authenticate: auth });
  const { watches, prices, events } = controllers;
  router
    .add('POST', '/watches', 'user', watches.create)
    .add('GET', '/watches', 'user', watches.list)
    .add('GET', '/watches/:id', 'user', watches.get)
    .add('PATCH', '/watches/:id', 'user', watches.update)
    .add('DELETE', '/watches/:id', 'user', watches.remove);
  if (prices) {
    router
      .add('POST', '/watches/:id/check', 'user', prices.check)
      .add('GET', '/watches/:id/history', 'user', prices.history)
      .add('GET', '/alerts', 'user', prices.listAlerts)
      .add('PATCH', '/alerts/:id', 'user', prices.markAlert)
      // Vercel Cron llama con GET; POST sirve para dispararlo a mano (ambos exigen CRON_SECRET).
      .add('GET', '/internal/check-due', 'cron', prices.checkDue)
      .add('POST', '/internal/check-due', 'cron', prices.checkDue);
  }
  if (events) {
    router
      .add('GET', '/internal/events/dispatch', 'cron', events.dispatch)
      .add('POST', '/internal/events/dispatch', 'cron', events.dispatch)
      .add('GET', '/internal/events/stats', 'cron', events.stats);
  }
  return router;
}

interface App {
  router: Router;
  dispatcher: EventDispatcher;
}

let cached: App | undefined;

/** Un cliente por instancia de la función serverless: la composición del dominio vive en `compose.ts`. */
function getApp(): App {
  if (!cached) {
    const domain = buildDomain({ sql: getSql(), provider: new StoreSearchPriceProvider(), events: isEventAlertsEnabled() });
    cached = {
      dispatcher: domain.dispatcher,
      router: buildRouter({
        watches: new WatchController(domain.watchService),
        prices: new PriceController(domain.priceService),
        events: new EventController(domain.dispatcher, domain.queue),
      }),
    };
  }
  return cached;
}

export const getRouter = (): Router => getApp().router;
/** Lo usa el servidor local para correr el despachador en bucle (en Vercel lo disparan el cron o un webhook). */
export const getDispatcher = (): EventDispatcher => getApp().dispatcher;
