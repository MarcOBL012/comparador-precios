import { z, type ZodTypeAny } from 'zod';
import { ValidationError } from '../errors.js';
import { CATEGORIAS } from '../productIdentification.js';

const MAX_PRECIO = 1_000_000;

const texto = (max: number) => z.string().trim().max(max);

/** Soles con hasta 2 decimales: lo que la columna numeric(10,2) puede guardar sin redondear. */
const precio = z.number().positive('Debe ser mayor a 0').max(MAX_PRECIO).multipleOf(0.01, 'Máximo 2 decimales');

export const CreateWatchSchema = z
  .object({
    producto: z
      .object({
        marca: texto(80).default(''),
        nombre: texto(200).min(1, 'El nombre es obligatorio'),
        presentacion: texto(60).default(''),
        categoria: z.enum(CATEGORIAS),
        tipo: texto(60).default(''),
      })
      .strict(),
    precioObjetivo: precio.nullable().optional(),
  })
  .strict();

export const UpdateWatchSchema = z
  .object({
    precioObjetivo: precio.nullable().optional(),
    estado: z.enum(['activa', 'pausada']).optional(),
  })
  .strict()
  .refine((body) => body.precioObjetivo !== undefined || body.estado !== undefined, {
    message: 'Indica al menos precioObjetivo o estado',
  });

export const ListWatchesQuerySchema = z.object({
  estado: z.enum(['activa', 'pausada', 'alcanzada']).optional(),
  limit: z.coerce.number().int().min(1).max(50).default(20),
  offset: z.coerce.number().int().min(0).default(0),
});

export const IdSchema = z.string().uuid('Identificador inválido');

/** Convierte el error de zod en un 400 con el detalle campo por campo. */
export function parse<S extends ZodTypeAny>(schema: S, data: unknown, mensaje = 'Datos inválidos'): z.infer<S> {
  const result = schema.safeParse(data);
  if (result.success) {
    return result.data;
  }
  throw new ValidationError(
    mensaje,
    result.error.issues.map((issue) => ({ campo: issue.path.join('.'), mensaje: issue.message }))
  );
}
