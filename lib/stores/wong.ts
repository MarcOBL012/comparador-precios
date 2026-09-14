import { searchVtexStore } from './vtexSearch.js';
import type { ProductQuery } from '../productIdentification.js';
import type { StoreProduct } from './types.js';

export function buscarWong(identification: ProductQuery): Promise<StoreProduct | null> {
  return searchVtexStore(
    { baseUrl: 'https://www.wong.pe', storeName: 'Wong', timeoutMs: 5000 },
    identification
  );
}
