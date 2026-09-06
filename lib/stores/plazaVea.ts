import { searchVtexStore } from './vtexSearch.js';
import type { ProductIdentification } from '../productIdentification.js';
import type { StoreProduct } from './types.js';

export function buscarPlazaVea(identification: ProductIdentification): Promise<StoreProduct | null> {
  return searchVtexStore(
    { baseUrl: 'https://www.plazavea.com.pe', storeName: 'Plaza Vea', timeoutMs: 5000 },
    identification
  );
}
