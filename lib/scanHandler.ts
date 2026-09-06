import { ValidationError, IdentificationError } from './errors';
import { identifyProduct } from './identifyProduct';
import type { ProductIdentification } from './productIdentification';

export interface ScanResponseBody {
  identification: ProductIdentification;
}

export async function handleScan(body: unknown): Promise<ScanResponseBody> {
  if (typeof body !== 'object' || body === null) {
    throw new ValidationError('El cuerpo de la petición debe ser un objeto JSON.');
  }

  const { image } = body as { image?: unknown };
  if (typeof image !== 'string' || !image.startsWith('data:image/')) {
    throw new ValidationError(
      'El campo "image" es obligatorio y debe ser una data URI de imagen (ej. "data:image/jpeg;base64,...").'
    );
  }

  let identification: ProductIdentification;
  try {
    identification = await identifyProduct(image);
  } catch (err) {
    throw new IdentificationError('No se pudo identificar el producto a partir de la imagen.', err);
  }

  return { identification };
}
