import { CATEGORIAS, type Categoria, type ProductQuery } from './productIdentification.js';
import { buscarLinkComoUltimoRecurso } from './stores/googleSearch.js';
import { buscarEnGoogleShopping } from './stores/googleShopping.js';
import { buscarMetro } from './stores/metro.js';
import { buscarOechsle } from './stores/oechsle.js';
import { buscarPlazaVea } from './stores/plazaVea.js';
import { buscarPromart } from './stores/promart.js';
import { buscarWong } from './stores/wong.js';
import type { StoreProduct, StoreResult } from './stores/types.js';

type DirectSearch = (query: ProductQuery) => Promise<StoreProduct | null>;

const DIRECT_STORES: Record<string, DirectSearch> = {
  'Plaza Vea': buscarPlazaVea,
  Wong: buscarWong,
  Metro: buscarMetro,
  Oechsle: buscarOechsle,
  Promart: buscarPromart,
};

interface Route {
  tiendas: string[];
  /** Google Shopping no lista abarrotes en Perú (verificado: 0 resultados), así que ahí no se gasta la consulta. */
  web: boolean;
}

// Solo "abarrotes" fue verificado sin resultados en Google Shopping Perú. Bebidas/limpieza/cuidado
// personal SÍ consultan Serper: una marca local que ninguna de las tres cadenas tiene en su catálogo
// (ej. una bebida que solo vende una tienda pequeña) igual debe aparecer con lo que salga ahí.
const SUPERMERCADOS: Route = { tiendas: ['Plaza Vea', 'Wong', 'Metro'], web: false };
const SUPERMERCADOS_CON_WEB: Route = { tiendas: ['Plaza Vea', 'Wong', 'Metro'], web: true };
const TIENDAS_POR_DEPARTAMENTO: Route = { tiendas: ['Plaza Vea', 'Oechsle', 'Promart'], web: true };

export const ROUTES: Record<Categoria, Route> = {
  abarrotes: SUPERMERCADOS,
  bebidas: SUPERMERCADOS_CON_WEB,
  limpieza: SUPERMERCADOS_CON_WEB,
  cuidado_personal: SUPERMERCADOS_CON_WEB,
  tecnologia: TIENDAS_POR_DEPARTAMENTO,
  electrohogar: TIENDAS_POR_DEPARTAMENTO,
  hogar: { tiendas: ['Promart', 'Oechsle', 'Plaza Vea'], web: true },
  ferreteria: { tiendas: ['Promart', 'Oechsle'], web: true },
  moda: { tiendas: ['Oechsle'], web: true },
  juguetes: { tiendas: ['Oechsle', 'Plaza Vea'], web: true },
  otros: TIENDAS_POR_DEPARTAMENTO,
};

export function isCategoria(value: unknown): value is Categoria {
  return typeof value === 'string' && (CATEGORIAS as readonly string[]).includes(value);
}

/**
 * Tiendas para una o más categorías. Con dos (la que eligió la persona y la que vio Gemini)
 * se consultan las de ambas: un chip mal marcado no debe dejar sin resultados.
 */
export function routeFor(...categorias: string[]): Route {
  const routes = [...new Set(categorias)].map((c) => (isCategoria(c) ? ROUTES[c] : ROUTES.otros));
  return {
    tiendas: [...new Set(routes.flatMap((r) => r.tiendas))],
    web: routes.some((r) => r.web),
  };
}

export interface DeviceContext {
  ahorroBateria: boolean;
}

/** El contexto es informativo: si llega mal formado se ignora en vez de rechazar el escaneo. */
export function parseDeviceContext(raw: unknown): DeviceContext {
  const ahorroBateria =
    typeof raw === 'object' && raw !== null && (raw as { ahorroBateria?: unknown }).ahorroBateria === true;
  return { ahorroBateria };
}

export type WebSearchStatus = 'realizada' | 'omitida_por_bateria' | 'no_aplica' | 'error';

export interface StoreSearchResult {
  tiendas: StoreResult[];
  busquedaWeb: WebSearchStatus;
}

/**
 * Google Shopping primero (precio estructurado); si no encuentra nada, una búsqueda web normal
 * como último recurso, solo para dar un link en vez de dejar el escaneo sin nada (ver
 * lib/stores/googleSearch.ts). Un error en cualquiera de las dos marca 'error' — igual que antes.
 */
async function searchWeb(query: ProductQuery, tiendasExcluidas: string[], apiKey: string): Promise<StoreResult[] | null> {
  try {
    const shopping = await buscarEnGoogleShopping(query, { apiKey, excluir: tiendasExcluidas });
    if (shopping.length > 0) {
      return shopping;
    }
    return await buscarLinkComoUltimoRecurso(query, { apiKey, excluir: tiendasExcluidas });
  } catch (err) {
    console.error('Búsqueda web (Serper) falló:', err);
    return null;
  }
}

async function searchDirect(tienda: string, query: ProductQuery): Promise<StoreResult> {
  try {
    const match = await DIRECT_STORES[tienda](query);
    if (match === null) {
      return { tienda, estado: 'no_encontrado' };
    }
    return { tienda, estado: 'encontrado', producto: match.producto, precio: match.precio, url: match.url };
  } catch (err) {
    return { tienda, estado: 'error', mensaje: err instanceof Error ? err.message : 'Error desconocido' };
  }
}

export interface SearchOptions {
  /** Categoría que la persona marcó antes de la foto; se suma a la que identificó Gemini. */
  categoriaElegida?: Categoria | null;
  /** null = sin búsqueda web. Por defecto se lee SERPER_API_KEY del entorno. */
  serperApiKey?: string | null;
}

export async function searchStores(
  query: ProductQuery,
  context: DeviceContext,
  options: SearchOptions = {}
): Promise<StoreSearchResult> {
  const serperApiKey = options.serperApiKey === undefined ? process.env.SERPER_API_KEY : options.serperApiKey;
  const route = options.categoriaElegida
    ? routeFor(options.categoriaElegida, query.categoria)
    : routeFor(query.categoria);

  // Con batería baja el celular espera con la radio encendida: la búsqueda web es la más lenta, se omite.
  let busquedaWeb: WebSearchStatus = 'no_aplica';
  if (route.web && serperApiKey) {
    busquedaWeb = context.ahorroBateria ? 'omitida_por_bateria' : 'realizada';
  }

  const [directas, web] = await Promise.all([
    Promise.all(route.tiendas.map((tienda) => searchDirect(tienda, query))),
    busquedaWeb === 'realizada' ? searchWeb(query, route.tiendas, serperApiKey!) : Promise.resolve([]),
  ]);

  if (web === null) {
    return { tiendas: directas, busquedaWeb: 'error' };
  }
  return { tiendas: [...directas, ...web], busquedaWeb };
}
