import { pickBestMatch, type MatchCandidate } from '../matching.js';
import type { ProductQuery } from '../productIdentification.js';
import type { StoreResult } from './types.js';

const SERPER_SHOPPING_URL = 'https://google.serper.dev/shopping';

// Reventa informal y marketplaces internacionales: precios de vendedores sueltos, no de una tienda.
const MARKETPLACES = ['mercadolibre', 'amazon', 'aliexpress', 'temu', 'shein', 'ebay', 'olx', 'facebook'];

interface SerperShoppingItem {
  title?: string;
  source?: string;
  link?: string;
  price?: string;
}

interface ShoppingCandidate extends MatchCandidate {
  source: string;
  precio: number;
  url: string;
}

export interface GoogleShoppingOptions {
  apiKey: string;
  /** Tiendas ya consultadas directo en esta búsqueda; sus ofertas en Shopping se omiten para no duplicarlas. */
  excluir: string[];
  limite?: number;
  timeoutMs?: number;
}

/** "Falabella Perú", "falabella.com.pe" y "www.falabella.com.pe" son la misma tienda. */
export function storeKey(source: string): string {
  return source
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/^www\./, '')
    .replace(/\.com\.pe$|\.pe$|\.com$/, '')
    .replace(/\bperu\b/g, '')
    .replace(/[^a-z0-9]/g, '');
}

/** Solo soles: "S/ 1,299.90" → 1299.9. Cualquier otra moneda se descarta. */
export function parseSoles(price: string | undefined): number | null {
  const match = price?.match(/S\/\.?\s*([\d.,]+)/);
  if (!match) {
    return null;
  }
  const value = Number(match[1].replace(/,/g, ''));
  return Number.isFinite(value) && value > 0 ? value : null;
}

/** "Kingstore Peru S.A.C." → "Kingstore", "metro.pe" → "Metro". */
export function displayName(sources: string[]): string {
  const named = sources.find((source) => !/\.(com|pe|net|store|shop)\b/i.test(source));
  if (!named) {
    const host = sources[0].replace(/^www\./i, '').split('.')[0];
    return host.charAt(0).toUpperCase() + host.slice(1);
  }
  return named
    .replace(/\s*—.*$/, '')
    .replace(/\b(S\.?A\.?C\.?|S\.?A\.?|E\.?I\.?R\.?L\.?|S\.?R\.?L\.?)\s*$/i, '')
    .replace(/\s+Per[uú]\s*$/i, '')
    .trim();
}

/**
 * Frases a probar en Serper, de la más específica a la más amplia. Un producto poco común
 * (marca local, empaque que Google no indexó tal cual) puede no aparecer con la frase exacta
 * pero sí con una más corta — probamos varias en vez de rendirnos con la primera vacía.
 * Se deduplica: si presentación/tipo están vacíos, no se repite la misma consulta dos veces.
 */
export function queryVariants(identification: ProductQuery): string[] {
  const variants: string[] = [];
  const add = (raw: string) => {
    const q = raw.trim().replace(/\s+/g, ' ');
    if (q && !variants.includes(q)) {
      variants.push(q);
    }
  };
  // 1. La más específica: marca + nombre (ya trae sabor/color/modelo, ver prompt de Gemini) + presentación.
  add(`${identification.marca} ${identification.nombre} ${identification.presentacion}`);
  // 2. Sin presentación: el tamaño exacto a veces no calza con cómo lo lista la tienda.
  add(`${identification.marca} ${identification.nombre}`);
  // 3. Última red: marca + lo mínimo que distingue el producto, por si el nombre completo
  //    (con una variante o sabor poco común) es justo lo que Google no tiene indexado.
  if (identification.tipo) {
    add(`${identification.marca} ${identification.tipo}`);
  }
  return variants;
}

async function fetchShoppingItems(q: string, apiKey: string, timeoutMs: number): Promise<SerperShoppingItem[]> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(SERPER_SHOPPING_URL, {
      method: 'POST',
      signal: controller.signal,
      headers: { 'X-API-KEY': apiKey, 'Content-Type': 'application/json' },
      body: JSON.stringify({ q, gl: 'pe', hl: 'es' }),
    });
    if (!response.ok) {
      throw new Error(`Serper respondió con estado ${response.status}`);
    }
    const body = (await response.json()) as { shopping?: SerperShoppingItem[] };
    return Array.isArray(body.shopping) ? body.shopping : [];
  } finally {
    clearTimeout(timeout);
  }
}

function matchItems(
  identification: ProductQuery,
  items: SerperShoppingItem[],
  excluded: string[],
  limite: number
): StoreResult[] {
  const bySource = new Map<string, { sources: string[]; candidates: ShoppingCandidate[] }>();
  for (const item of items) {
    const precio = parseSoles(item.price);
    if (!item.title || !item.source || !item.link || precio === null) {
      continue;
    }
    const key = storeKey(item.source);
    if (!key || MARKETPLACES.some((m) => key.includes(m)) || excluded.some((e) => key.includes(e))) {
      continue;
    }
    const group = bySource.get(key) ?? { sources: [], candidates: [] };
    group.sources.push(item.source);
    group.candidates.push({ productName: item.title, brand: '', source: item.source, precio, url: item.link });
    bySource.set(key, group);
  }

  // "Kingstore Peru S.A.C." y "kingstore.pe" caen en grupos distintos pero se muestran igual: queda la más barata.
  const cheapestByName = new Map<string, StoreResult>();
  for (const { sources, candidates } of bySource.values()) {
    const best = pickBestMatch(identification, candidates);
    if (!best) {
      continue;
    }
    const tienda = displayName(sources);
    const previous = cheapestByName.get(tienda.toLowerCase());
    if (!previous || best.precio < previous.precio!) {
      cheapestByName.set(tienda.toLowerCase(), {
        tienda,
        estado: 'encontrado',
        producto: best.productName,
        precio: best.precio,
        url: best.url,
        fuente: 'web',
      });
    }
  }

  return [...cheapestByName.values()].sort((a, b) => a.precio! - b.precio!).slice(0, limite);
}

export async function buscarEnGoogleShopping(
  identification: ProductQuery,
  { apiKey, excluir, limite = 3, timeoutMs = 8000 }: GoogleShoppingOptions
): Promise<StoreResult[]> {
  const excluded = excluir.map(storeKey);
  let lastError: unknown = null;

  for (const q of queryVariants(identification)) {
    try {
      const items = await fetchShoppingItems(q, apiKey, timeoutMs);
      const matched = matchItems(identification, items, excluded, limite);
      if (matched.length > 0) {
        return matched;
      }
      // Serper respondió pero nada calzó: no es un error, solo esta frase no encontró nada. Seguir probando.
      lastError = null;
    } catch (err) {
      // Un timeout o error puntual en una variante no debe tumbar la búsqueda si otra variante funciona.
      lastError = err;
    }
  }

  if (lastError) {
    throw lastError;
  }
  return [];
}
