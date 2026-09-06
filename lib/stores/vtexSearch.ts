import { pickBestMatch, type MatchCandidate } from '../matching';
import type { ProductIdentification } from '../productIdentification';
import type { StoreProduct } from './types';

interface VtexProduct {
  productName: string;
  brand: string;
  link: string;
  items?: Array<{
    sellers?: Array<{
      commertialOffer?: {
        Price: number;
        IsAvailable: boolean;
      };
    }>;
  }>;
}

interface VtexCandidate extends MatchCandidate {
  precio: number;
  url: string;
}

export interface VtexStoreConfig {
  baseUrl: string;
  storeName: string;
  timeoutMs: number;
}

export async function searchVtexStore(
  config: VtexStoreConfig,
  identification: ProductIdentification
): Promise<StoreProduct | null> {
  const term = `${identification.marca} ${identification.nombre}`.trim();
  const url = `${config.baseUrl}/api/catalog_system/pub/products/search/${encodeURIComponent(term)}?_from=0&_to=9`;

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), config.timeoutMs);

  let products: VtexProduct[];
  try {
    const response = await fetch(url, { signal: controller.signal });
    if (!response.ok) {
      throw new Error(`${config.storeName} respondió con estado ${response.status}`);
    }
    products = (await response.json()) as VtexProduct[];
  } finally {
    clearTimeout(timeout);
  }

  const candidates: VtexCandidate[] = [];
  for (const product of products) {
    const offer = product.items?.[0]?.sellers?.[0]?.commertialOffer;
    if (offer && offer.IsAvailable && offer.Price > 0) {
      candidates.push({
        productName: product.productName,
        brand: product.brand,
        precio: offer.Price,
        url: product.link,
      });
    }
  }

  const best = pickBestMatch(identification, candidates);
  if (!best) {
    return null;
  }
  return { producto: best.productName, precio: best.precio, url: best.url };
}
