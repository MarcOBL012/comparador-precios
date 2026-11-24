import { UpstreamError } from '../errors.js';
import type { Watch } from '../watches/types.js';
import type { ObservationOutcome, PriceProvider, PriceRepository, RecordContext } from './types.js';

/**
 * Consulta las tiendas de un seguimiento y guarda lo encontrado (publicando `watch.checked`).
 * Lo comparten «Revisar ahora» (el usuario espera la respuesta) y el consumidor `WatchChecker`
 * (revisión programada): es la misma operación, solo cambia quién la dispara.
 */
export class CheckRecorder {
  constructor(
    private readonly prices: PriceRepository,
    private readonly provider: PriceProvider
  ) {}

  async record(userId: string, watch: Watch, ctx: RecordContext = {}): Promise<ObservationOutcome> {
    let offers;
    try {
      offers = await this.provider.fetchOffers(watch.producto);
    } catch (err) {
      throw err instanceof UpstreamError ? err : new UpstreamError('No pudimos consultar las tiendas ahora.', err);
    }
    return this.prices.recordObservations(
      { id: watch.id, userId, productId: watch.producto.id, precioObjetivo: watch.precioObjetivo, estado: watch.estado },
      offers,
      ctx
    );
  }
}
