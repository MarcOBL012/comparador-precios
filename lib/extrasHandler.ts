import { ValidationError } from './errors.js';
import { ProductQuerySchema } from './productIdentification.js';
import {
  generateInsights,
  isModo,
  limpiarPerfil,
  modoPara,
  type Modo,
  type ProductInsights,
} from './productInsights.js';
import { buscarRuta, MAX_KM_A_PIE, type ModoRuta, type Ruta } from './route.js';
import { buscarTiendasCercanas, type Coordenadas, type TiendaCercana } from './stores/nearbyStores.js';

function requireObject(body: unknown): Record<string, unknown> {
  if (typeof body !== 'object' || body === null) {
    throw new ValidationError('El cuerpo de la petición debe ser un objeto JSON.');
  }
  return body as Record<string, unknown>;
}

function isCoordenadas(value: unknown): value is Coordenadas {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const { lat, lng } = value as Record<string, unknown>;
  return (
    typeof lat === 'number' &&
    typeof lng === 'number' &&
    Number.isFinite(lat) &&
    Number.isFinite(lng) &&
    Math.abs(lat) <= 90 &&
    Math.abs(lng) <= 180
  );
}

export interface InsightsResponseBody {
  modo: Modo;
  insights: ProductInsights;
}

/**
 * `modo` ausente o "auto" → se elige según la categoría del producto (ver modoPara).
 * `perfil` (opcional): intereses de la persona para personalizar ("Bajar azúcar", "Gaming"…).
 */
export async function handleInsights(body: unknown): Promise<InsightsResponseBody> {
  const { identification, modo, perfil } = requireObject(body);
  const parsed = ProductQuerySchema.safeParse(identification);
  if (!parsed.success || `${parsed.data.marca}${parsed.data.nombre}`.trim() === '') {
    throw new ValidationError('El campo "identification" necesita al menos marca o nombre del producto.');
  }
  if (modo !== undefined && modo !== 'auto' && !isModo(modo)) {
    throw new ValidationError('El campo "modo" debe ser "auto", "salud", "tecnologia" o "curiosidades".');
  }
  const modoFinal = isModo(modo) ? modo : modoPara(parsed.data.categoria);
  return { modo: modoFinal, insights: await generateInsights(parsed.data, modoFinal, limpiarPerfil(perfil)) };
}

export interface NearbyStoresResponseBody {
  tiendas: TiendaCercana[];
}

const MAX_CADENAS = 10;

export async function handleNearbyStores(body: unknown): Promise<NearbyStoresResponseBody> {
  const { cadenas, lat, lng } = requireObject(body);
  if (
    !Array.isArray(cadenas) ||
    cadenas.length === 0 ||
    cadenas.length > MAX_CADENAS ||
    !cadenas.every((c) => typeof c === 'string')
  ) {
    throw new ValidationError(`El campo "cadenas" debe ser una lista de 1 a ${MAX_CADENAS} nombres de tienda.`);
  }
  const ubicacion = { lat, lng };
  if (!isCoordenadas(ubicacion)) {
    throw new ValidationError('Los campos "lat" y "lng" deben ser coordenadas válidas.');
  }
  return { tiendas: await buscarTiendasCercanas(cadenas as string[], ubicacion) };
}

export interface RouteResponseBody {
  /** null = el servidor de rutas no encontró camino; la app muestra la línea recta y el botón a Google Maps. */
  ruta: Ruta | null;
}

/** Distancia en línea recta (Haversine): decide si se propone ir a pie o en auto. */
export function distanciaKm(a: Coordenadas, b: Coordenadas): number {
  const rad = (g: number) => (g * Math.PI) / 180;
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.sqrt(h));
}

export async function handleRoute(body: unknown): Promise<RouteResponseBody> {
  const { origen, destino } = requireObject(body);
  if (!isCoordenadas(origen) || !isCoordenadas(destino)) {
    throw new ValidationError('Los campos "origen" y "destino" deben ser coordenadas { lat, lng } válidas.');
  }
  const modo: ModoRuta = distanciaKm(origen, destino) <= MAX_KM_A_PIE ? 'pie' : 'auto';
  return { ruta: await buscarRuta(origen, destino, modo) };
}
