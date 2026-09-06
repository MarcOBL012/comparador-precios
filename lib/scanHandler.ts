import { ValidationError, IdentificationError } from './errors.js';
import { identifyProduct } from './identifyProduct.js';
import type { ProductIdentification } from './productIdentification.js';
import { buscarPlazaVea } from './stores/plazaVea.js';
import { buscarWong } from './stores/wong.js';
import type { StoreProduct } from './stores/types.js';

const DATA_URI_PATTERN = /^data:image\/(jpeg|png|gif|webp);base64,([A-Za-z0-9+/]+={0,2})$/;
const MAX_IMAGE_BASE64_LENGTH = 4_000_000; // ~3MB decoded; stays under Vercel's 4.5MB request body limit
const CONFIDENCE_THRESHOLD = 0.5;

export interface StoreResult {
  tienda: string;
  estado: 'encontrado' | 'no_encontrado' | 'error';
  producto?: string;
  precio?: number;
  url?: string;
  mensaje?: string;
}

export interface ScanResponseBody {
  identification: ProductIdentification;
  tiendas: StoreResult[];
}

async function searchStore(
  tienda: string,
  search: (identification: ProductIdentification) => Promise<StoreProduct | null>,
  identification: ProductIdentification
): Promise<StoreResult> {
  try {
    const match = await search(identification);
    if (match === null) {
      return { tienda, estado: 'no_encontrado' };
    }
    return { tienda, estado: 'encontrado', producto: match.producto, precio: match.precio, url: match.url };
  } catch (err) {
    return { tienda, estado: 'error', mensaje: err instanceof Error ? err.message : 'Error desconocido' };
  }
}

export async function handleScan(body: unknown): Promise<ScanResponseBody> {
  if (typeof body !== 'object' || body === null) {
    throw new ValidationError('El cuerpo de la petición debe ser un objeto JSON.');
  }

  const { image } = body as { image?: unknown };
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
    identification = await identifyProduct(image);
  } catch (err) {
    throw new IdentificationError('No se pudo identificar el producto a partir de la imagen.', err);
  }

  if (identification.confianza < CONFIDENCE_THRESHOLD) {
    return { identification, tiendas: [] };
  }

  const tiendas = await Promise.all([
    searchStore('Plaza Vea', buscarPlazaVea, identification),
    searchStore('Wong', buscarWong, identification),
  ]);

  return { identification, tiendas };
}
