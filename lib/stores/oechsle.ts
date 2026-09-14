import { searchVtexStore } from './vtexSearch.js';
import type { ProductQuery } from '../productIdentification.js';
import type { StoreProduct } from './types.js';

export function buscarOechsle(identification: ProductQuery): Promise<StoreProduct | null> {
  return searchVtexStore(
    { baseUrl: 'https://www.oechsle.pe', storeName: 'Oechsle', timeoutMs: 5000 },
    identification
  );
}
