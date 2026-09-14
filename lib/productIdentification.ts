import { z } from 'zod';

export const CATEGORIAS = [
  'abarrotes',
  'bebidas',
  'limpieza',
  'cuidado_personal',
  'tecnologia',
  'electrohogar',
  'hogar',
  'ferreteria',
  'moda',
  'juguetes',
  'otros',
] as const;

export type Categoria = (typeof CATEGORIAS)[number];

// Lo mínimo para buscar un producto en tiendas. `categoria` es texto libre porque los
// escaneos guardados en el celular antes de fijar las categorías traen valores como
// "electrodomésticos"; el ruteo trata lo que no reconoce como "otros".
export const ProductQuerySchema = z.object({
  marca: z.string(),
  nombre: z.string(),
  presentacion: z.string(),
  categoria: z.string(),
  // Escaneos anteriores a este campo no lo tienen; vacío = no se exige tipo al buscar.
  tipo: z.string().default(''),
});

export type ProductQuery = z.input<typeof ProductQuerySchema>;

export const ProductIdentificationSchema = ProductQuerySchema.extend({
  categoria: z.enum(CATEGORIAS),
  tipo: z.string(),
  confianza: z.number().min(0).max(1),
});

export type ProductIdentification = z.infer<typeof ProductIdentificationSchema>;
