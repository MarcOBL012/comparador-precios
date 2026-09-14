import { searchVtexStore } from './vtexSearch.js';
import type { ProductQuery } from '../productIdentification.js';
import type { StoreProduct } from './types.js';

export function buscarMetro(identification: ProductQuery): Promise<StoreProduct | null> {
  return searchVtexStore(
    { baseUrl: 'https://www.metro.pe', storeName: 'Metro', timeoutMs: 5000 },
    identification
  );
}
