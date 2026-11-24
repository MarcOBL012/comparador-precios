import type { Sql } from './db/client.js';
import { isDispatchEnabled, PgEventBus } from './events/bus.js';
import { AlertEvaluator } from './events/consumers/alertEvaluator.js';
import { MetricsRecorder } from './events/consumers/metricsRecorder.js';
import { WatchChecker } from './events/consumers/watchChecker.js';
import { EventDispatcher, type DispatcherOptions } from './events/dispatcher.js';
import { PgEventQueue } from './events/repository.js';
import { CheckScheduler } from './events/scheduler.js';
import { ConsumerRegistry } from './events/subscriptions.js';
import type { Consumer } from './events/types.js';
import { CheckRecorder } from './prices/checkRecorder.js';
import { EventCheckFlow } from './prices/eventFlow.js';
import { PgPriceRepository } from './prices/repository.js';
import { PriceService } from './prices/service.js';
import type { PriceProvider } from './prices/types.js';
import { PgWatchRepository } from './watches/repository.js';
import { WatchService } from './watches/service.js';

export interface DomainOptions {
  sql: Sql;
  provider: PriceProvider;
  /**
   * true = revisiones y alertas por eventos (EVENTS_ALERTS=on): registra AlertEvaluator y WatchChecker
   * y usa el flujo por eventos. false = el modo anterior. Con true el bus siempre escribe el envelope completo.
   */
  events: boolean;
  now?: () => Date;
  extraConsumers?: Consumer[];
  dispatcherOptions?: DispatcherOptions;
  log?: (line: string) => void;
}

/**
 * Raíz de composición del dominio: aquí (y solo aquí) se conectan base de datos → bus → repositorios →
 * consumidores → servicios. La usan la API (`lib/app.ts`) y las pruebas de integración, para probar
 * exactamente lo que corre en producción.
 */
export function buildDomain(options: DomainOptions) {
  const { sql, provider, events } = options;
  const now = options.now ?? (() => new Date());

  // El bus necesita saber quién escucha qué para crear las entregas, y los consumidores necesitan el
  // bus para publicar: el registro se completa después, y el bus lo consulta recién al publicar.
  let registry: ConsumerRegistry | undefined;
  const bus = new PgEventBus(
    (type) => registry?.consumersFor(type) ?? [],
    () => events || isDispatchEnabled()
  );

  const watchRepo = new PgWatchRepository(sql, bus);
  const prices = new PgPriceRepository(sql, bus);
  const recorder = new CheckRecorder(prices, provider);

  const consumers: Consumer[] = [new MetricsRecorder(options.log)];
  if (events) {
    consumers.push(new AlertEvaluator(sql, bus), new WatchChecker(watchRepo, recorder));
  }
  consumers.push(...(options.extraConsumers ?? []));
  registry = new ConsumerRegistry(consumers);

  const queue = new PgEventQueue(sql);
  const dispatcher = new EventDispatcher(queue, registry, options.dispatcherOptions);
  const flow = events ? new EventCheckFlow(recorder, prices, watchRepo, dispatcher, new CheckScheduler(sql, prices, bus), now) : undefined;

  return {
    bus,
    queue,
    registry,
    dispatcher,
    watchRepo,
    prices,
    watchService: new WatchService(watchRepo),
    priceService: new PriceService(watchRepo, prices, provider, now, flow),
  };
}

export type Domain = ReturnType<typeof buildDomain>;

/** EVENTS_ALERTS=on activa las alertas por eventos; requiere además EVENTS_DISPATCH=on (y la migración 006). */
export const isEventAlertsEnabled = (): boolean => process.env.EVENTS_ALERTS === 'on' && isDispatchEnabled();
