import { searchVtexStore } from './vtexSearch';
import type { ProductIdentification } from '../productIdentification';
import type { StoreProduct } from './types';

export function buscarWong(identification: ProductIdentification): Promise<StoreProduct | null> {
  return searchVtexStore(
    { baseUrl: 'https://www.wong.pe', storeName: 'Wong', timeoutMs: 5000 },
    identification
  );
}
