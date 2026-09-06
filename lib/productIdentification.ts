import { z } from 'zod';

export const ProductIdentificationSchema = z.object({
  marca: z.string(),
  nombre: z.string(),
  presentacion: z.string(),
  categoria: z.string(),
  confianza: z.number().min(0).max(1),
});

export type ProductIdentification = z.infer<typeof ProductIdentificationSchema>;
