import { authenticate } from './auth.js';
import { getSql } from './db/client.js';
import { Router } from './http/router.js';
import { PriceController } from './prices/controller.js';
import { StoreSearchPriceProvider } from './prices/provider.js';
import { PgPriceRepository } from './prices/repository.js';
import { PriceService } from './prices/service.js';
import { WatchController } from './watches/controller.js';
import { PgWatchRepository } from './watches/repository.js';
import { WatchService } from './watches/service.js';

export interface AppControllers {
  watches: WatchController;
  /** Opcional solo para poder probar los seguimientos sin la parte de precios. */
  prices?: PriceController;
}

/** Tabla de rutas de la API v1. Cada fila: método, ruta, quién puede llamarla y qué controlador la atiende. */
export function buildRouter(controllers: AppControllers, auth = authenticate): Router {
  const router = new Router({ authenticate: auth });
  const { watches, prices } = controllers;
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
  return router;
}

let cached: Router | undefined;

/** Raíz de composición: aquí (y solo aquí) se conectan base de datos → repositorios → servicios → controladores. */
export function getRouter(): Router {
  if (!cached) {
    const sql = getSql();
    const watchRepo = new PgWatchRepository(sql);
    cached = buildRouter({
      watches: new WatchController(new WatchService(watchRepo)),
      prices: new PriceController(new PriceService(watchRepo, new PgPriceRepository(sql), new StoreSearchPriceProvider())),
    });
  }
  return cached;
}
