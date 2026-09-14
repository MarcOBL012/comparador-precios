import { searchVtexStore } from './vtexSearch.js';
import type { ProductQuery } from '../productIdentification.js';
import type { StoreProduct } from './types.js';

export function buscarPromart(identification: ProductQuery): Promise<StoreProduct | null> {
  return searchVtexStore(
    { baseUrl: 'https://www.promart.pe', storeName: 'Promart', timeoutMs: 5000 },
    identification
  );
}
