import { searchVtexStore } from './vtexSearch.js';
import type { ProductQuery } from '../productIdentification.js';
import type { StoreProduct } from './types.js';

export function buscarPlazaVea(identification: ProductQuery): Promise<StoreProduct | null> {
  return searchVtexStore(
    { baseUrl: 'https://www.plazavea.com.pe', storeName: 'Plaza Vea', timeoutMs: 5000 },
    identification
  );
}
