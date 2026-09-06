import { searchVtexStore } from './vtexSearch';
import type { ProductIdentification } from '../productIdentification';
import type { StoreProduct } from './types';

export function buscarPlazaVea(identification: ProductIdentification): Promise<StoreProduct | null> {
  return searchVtexStore(
    { baseUrl: 'https://www.plazavea.com.pe', storeName: 'Plaza Vea', timeoutMs: 5000 },
    identification
  );
}
