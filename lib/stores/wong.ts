import { searchVtexStore } from './vtexSearch.js';
import type { ProductIdentification } from '../productIdentification.js';
import type { StoreProduct } from './types.js';

export function buscarWong(identification: ProductIdentification): Promise<StoreProduct | null> {
  return searchVtexStore(
    { baseUrl: 'https://www.wong.pe', storeName: 'Wong', timeoutMs: 5000 },
    identification
  );
}
