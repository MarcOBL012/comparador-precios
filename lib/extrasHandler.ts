import { ValidationError } from './errors.js';
import { ProductQuerySchema } from './productIdentification.js';
import { generateInsights, isModo, modoPara, type Modo, type ProductInsights } from './productInsights.js';
import { buscarTiendasCercanas, type TiendaCercana } from './stores/nearbyStores.js';

function requireObject(body: unknown): Record<string, unknown> {
  if (typeof body !== 'object' || body === null) {
    throw new ValidationError('El cuerpo de la petición debe ser un objeto JSON.');
  }
  return body as Record<string, unknown>;
}

export interface InsightsResponseBody {
  modo: Modo;
  insights: ProductInsights;
}

/** `modo` ausente o "auto" → se elige según la categoría del producto (ver modoPara). */
export async function handleInsights(body: unknown): Promise<InsightsResponseBody> {
  const { identification, modo } = requireObject(body);
  const parsed = ProductQuerySchema.safeParse(identification);
  if (!parsed.success || `${parsed.data.marca}${parsed.data.nombre}`.trim() === '') {
    throw new ValidationError('El campo "identification" necesita al menos marca o nombre del producto.');
  }
  if (modo !== undefined && modo !== 'auto' && !isModo(modo)) {
    throw new ValidationError('El campo "modo" debe ser "auto", "salud", "tecnologia" o "curiosidades".');
  }
  const modoFinal = isModo(modo) ? modo : modoPara(parsed.data.categoria);
  return { modo: modoFinal, insights: await generateInsights(parsed.data, modoFinal) };
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
  if (typeof lat !== 'number' || typeof lng !== 'number' || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
    throw new ValidationError('Los campos "lat" y "lng" deben ser coordenadas válidas.');
  }
  return { tiendas: await buscarTiendasCercanas(cadenas as string[], { lat, lng }) };
}
