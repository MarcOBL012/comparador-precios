import { UpstreamError } from '../errors.js';
import { searchStores } from '../searchStores.js';
import type { ProductInput } from '../watches/types.js';
import type { Offer, PriceProvider } from './types.js';

/**
 * Adaptador sobre la búsqueda de precios que ya existe (tiendas VTEX + Google Shopping).
 * Lo nuevo de este taller no es buscar precios sino persistirlos, compararlos con el historial
 * y reaccionar con reglas; por eso la búsqueda queda detrás de la interfaz PriceProvider.
 */
export class StoreSearchPriceProvider implements PriceProvider {
  async fetchOffers(product: ProductInput): Promise<Offer[]> {
    const { tiendas } = await searchStores(product, { ahorroBateria: false });
    if (tiendas.length > 0 && tiendas.every((t) => t.estado === 'error')) {
      throw new UpstreamError('No pudimos consultar las tiendas ahora. Intenta de nuevo en unos minutos.');
    }
    return tiendas
      .filter((t) => t.estado === 'encontrado' && typeof t.precio === 'number' && t.precio > 0)
      .map((t) => ({
        tienda: t.tienda,
        titulo: t.producto ?? product.nombre,
        precio: t.precio!,
        url: t.url ?? null,
        fuente: t.fuente === 'web' ? ('shopping' as const) : ('directa' as const),
      }));
  }
}
