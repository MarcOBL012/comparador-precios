import { ValidationError, IdentificationError } from './errors.js';
import { identifyProduct } from './identifyProduct.js';
import { ProductQuerySchema, type ProductIdentification, type ProductQuery } from './productIdentification.js';
import { isCategoria, parseDeviceContext, searchStores, type WebSearchStatus } from './searchStores.js';
import type { StoreResult } from './stores/types.js';

const DATA_URI_PATTERN = /^data:image\/(jpeg|png|gif|webp);base64,([A-Za-z0-9+/]+={0,2})$/;
const MAX_IMAGE_BASE64_LENGTH = 4_000_000; // ~3MB decoded; stays under Vercel's 4.5MB request body limit
const CONFIDENCE_THRESHOLD = 0.5;

export interface ScanResponseBody {
  identification: ProductIdentification;
  tiendas: StoreResult[];
  busquedaWeb: WebSearchStatus;
}

export interface PricesResponseBody {
  tiendas: StoreResult[];
  busquedaWeb: WebSearchStatus;
}

function requireObject(body: unknown): Record<string, unknown> {
  if (typeof body !== 'object' || body === null) {
    throw new ValidationError('El cuerpo de la petición debe ser un objeto JSON.');
  }
  return body as Record<string, unknown>;
}

export async function handleScan(body: unknown): Promise<ScanResponseBody> {
  const { image, contexto, categoria } = requireObject(body);
  // Opcional y solo una pista: una categoría desconocida se ignora en vez de rechazar la foto.
  const categoriaElegida = isCategoria(categoria) ? categoria : null;
  if (typeof image !== 'string' || !DATA_URI_PATTERN.test(image)) {
    throw new ValidationError(
      'El campo "image" es obligatorio y debe ser una data URI de imagen jpeg, png, gif o webp en base64 (ej. "data:image/jpeg;base64,...").'
    );
  }
  if (image.length > MAX_IMAGE_BASE64_LENGTH) {
    throw new ValidationError('La imagen es demasiado grande. Usa una foto comprimida de menos de 3MB.');
  }

  let identification: ProductIdentification;
  try {
    identification = await identifyProduct(image, categoriaElegida);
  } catch (err) {
    throw new IdentificationError('No se pudo identificar el producto a partir de la imagen.', err);
  }

  if (identification.confianza < CONFIDENCE_THRESHOLD) {
    return { identification, tiendas: [], busquedaWeb: 'no_aplica' };
  }

  const result = await searchStores(identification, parseDeviceContext(contexto), { categoriaElegida });
  return { identification, ...result };
}

/** Vuelve a consultar precios de un producto ya identificado (lista de compras, historial). */
export async function handlePrices(body: unknown): Promise<PricesResponseBody> {
  const { identification, contexto } = requireObject(body);
  const parsed = ProductQuerySchema.safeParse(identification);
  if (!parsed.success) {
    throw new ValidationError(
      'El campo "identification" es obligatorio y debe tener marca, nombre, presentacion y categoria como texto.'
    );
  }
  const query: ProductQuery = parsed.data;
  if (`${query.marca}${query.nombre}`.trim() === '') {
    throw new ValidationError('La identificación necesita al menos marca o nombre para buscar precios.');
  }
  return searchStores(query, parseDeviceContext(contexto));
}
