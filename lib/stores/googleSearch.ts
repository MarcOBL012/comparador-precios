import { pickBestMatch, type MatchCandidate } from '../matching.js';
import type { ProductQuery } from '../productIdentification.js';
import type { StoreResult } from './types.js';
import { displayName, storeKey } from './googleShopping.js';

const SERPER_SEARCH_URL = 'https://google.serper.dev/search';

// Además de reventa informal, se descartan sitios de contenido que nunca son la página de un
// producto en venta: no tiene sentido "linkear" un video de YouTube o un post de Instagram.
const NO_RETAIL = [
  'mercadolibre',
  'amazon',
  'aliexpress',
  'temu',
  'shein',
  'ebay',
  'olx',
  'facebook',
  'instagram',
  'tiktok',
  'youtube',
  'wikipedia',
  'pinterest',
  'twitter',
  'linkedin',
  'reddit',
];

interface SerperOrganicItem {
  title?: string;
  link?: string;
}

interface WebLinkCandidate extends MatchCandidate {
  link: string;
}

export interface GoogleSearchOptions {
  apiKey: string;
  /** Tiendas ya consultadas directo; sus páginas en la búsqueda web se omiten para no duplicarlas. */
  excluir: string[];
  timeoutMs?: number;
}

/**
 * Último recurso cuando ni las tiendas directas ni Google Shopping encontraron el producto
 * (ver searchStores.ts): una búsqueda web normal de Serper, no estructurada como Shopping, así
 * que SIN precio — el objetivo es dar al menos un link a una página donde el producto sí aparece,
 * en vez de dejar el escaneo sin nada.
 */
export async function buscarLinkComoUltimoRecurso(
  identification: ProductQuery,
  { apiKey, excluir, timeoutMs = 8000 }: GoogleSearchOptions
): Promise<StoreResult[]> {
  const q = `${identification.marca} ${identification.nombre} ${identification.presentacion}`.trim();
  const excluded = excluir.map(storeKey);

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  let items: SerperOrganicItem[];
  try {
    const response = await fetch(SERPER_SEARCH_URL, {
      method: 'POST',
      signal: controller.signal,
      headers: { 'X-API-KEY': apiKey, 'Content-Type': 'application/json' },
      body: JSON.stringify({ q, gl: 'pe', hl: 'es' }),
    });
    if (!response.ok) {
      throw new Error(`Serper (búsqueda web) respondió con estado ${response.status}`);
    }
    const body = (await response.json()) as { organic?: SerperOrganicItem[] };
    items = Array.isArray(body.organic) ? body.organic : [];
  } finally {
    clearTimeout(timeout);
  }

  const candidates: WebLinkCandidate[] = [];
  for (const item of items) {
    if (!item.title || !item.link) {
      continue;
    }
    let hostname: string;
    try {
      hostname = new URL(item.link).hostname;
    } catch {
      continue;
    }
    const key = storeKey(hostname);
    if (!key || NO_RETAIL.some((m) => key.includes(m)) || excluded.some((e) => key.includes(e))) {
      continue;
    }
    candidates.push({ productName: item.title, brand: '', link: item.link });
  }

  // Serper ya trae los resultados ordenados por relevancia: el primero que además calce con la
  // identificación (marca/tipo) es la mejor apuesta, no hace falta comparar precios porque no hay.
  const best = pickBestMatch(identification, candidates);
  if (!best) {
    return [];
  }
  const tienda = displayName([new URL(best.link).hostname]);
  return [{ tienda, estado: 'encontrado', producto: best.productName, url: best.link, fuente: 'busqueda_web' }];
}
