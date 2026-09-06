import { ValidationError, IdentificationError } from './errors';
import { identifyProduct } from './identifyProduct';
import type { ProductIdentification } from './productIdentification';

export interface ScanResponseBody {
  identification: ProductIdentification;
}

const DATA_URI_PATTERN = /^data:image\/(jpeg|png|gif|webp);base64,([A-Za-z0-9+/]+={0,2})$/;
const MAX_IMAGE_BASE64_LENGTH = 4_000_000; // ~3MB decoded; stays under Vercel's 4.5MB request body limit

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

  return { identification };
}
