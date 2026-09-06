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
    let response: Response;
    try {
      response = await fetch(url, {
        signal: controller.signal,
        headers: {
          Accept: 'application/json',
          'User-Agent': 'ComparadorPreciosPeru/1.0',
        },
      });
    } catch (err) {
      const name = err instanceof Error ? err.name : undefined;
      if (name === 'AbortError') {
        throw new Error(`${config.storeName}: tiempo de espera agotado (${config.timeoutMs}ms)`);
      }
      throw err;
    }

    if (!response.ok) {
      throw new Error(`${config.storeName} respondió con estado ${response.status}`);
    }

    const body: unknown = await response.json();
    if (!Array.isArray(body)) {
      throw new Error(`${config.storeName} devolvió una respuesta inesperada`);
    }
    products = body as VtexProduct[];
  } finally {
    clearTimeout(timeout);
  }

  const candidates: VtexCandidate[] = [];
  for (const product of products) {
    const offer = product.items?.[0]?.sellers?.[0]?.commertialOffer;
    if (offer && offer.IsAvailable && offer.Price > 0 && product.productName && product.link) {
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
