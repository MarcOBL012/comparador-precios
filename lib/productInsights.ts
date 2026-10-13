import { generateText, Output } from 'ai';
import { z } from 'zod';
import { MODEL } from './identifyProduct.js';
import type { ProductQuery } from './productIdentification.js';

export const MODOS = ['salud', 'tecnologia', 'curiosidades'] as const;
export type Modo = (typeof MODOS)[number];

export function isModo(value: unknown): value is Modo {
  return typeof value === 'string' && (MODOS as readonly string[]).includes(value);
}

/** "auto" elige el modo según lo que se escaneó: un consejo de salud para un mouse no aporta nada. */
export function modoPara(categoria: string): Modo {
  switch (categoria) {
    case 'abarrotes':
    case 'bebidas':
    case 'cuidado_personal':
      return 'salud';
    case 'tecnologia':
    case 'electrohogar':
      return 'tecnologia';
    default:
      return 'curiosidades';
  }
}

export const ProductInsightsSchema = z.object({
  titular: z.string(),
  semaforo: z.enum(['verde', 'ambar', 'rojo', 'neutro']),
  etiquetaSemaforo: z.string(),
  puntos: z.array(z.object({ emoji: z.string(), texto: z.string() })).max(3),
  consejo: z.string(),
  datoCurioso: z.string(),
});

export type ProductInsights = z.infer<typeof ProductInsightsSchema>;

const COMUN = `Escribe en español peruano, cercano y positivo, como un amigo que sabe del tema — nunca alarmista ni de venta.
Sé específico a ESTE producto, no genérico. Frases cortas: cada texto de "puntos" tiene como máximo 16 palabras.
No inventes cifras exactas (calorías, mAh, precios): si das un valor, que sea uno típico de ese tipo de producto y márcalo con "aprox.".
Devuelve:
- titular: una frase gancho de máximo 8 palabras que despierte curiosidad sobre el producto.
- puntos: exactamente 3, cada uno con un emoji que lo represente y un texto breve.
- consejo: una acción concreta que la persona pueda hacer hoy con este producto (máximo 20 palabras).
- datoCurioso: un dato sorprendente y verdadero relacionado con el producto o su tipo (máximo 25 palabras).`;

const POR_MODO: Record<Modo, string> = {
  salud: `Modo SALUD. Evalúa qué tan recomendable es para el consumo habitual según lo típico de este tipo de producto (azúcar, sodio, grasas, ultraprocesado, aporte nutricional).
- semaforo: "verde" (buena opción diaria), "ambar" (con moderación) o "rojo" (ocasional). etiquetaSemaforo: 2 a 4 palabras que lo expliquen (ej. "Buena opción diaria").
- puntos: lo bueno, lo que hay que vigilar y para quién conviene o no.
No es consejo médico: no diagnostiques ni prometas curas. Si es cuidado personal, habla de uso seguro y piel/cabello.`,
  tecnologia: `Modo TECNOLOGÍA. Explica para qué le sirve de verdad a la persona y cómo sacarle el máximo.
- semaforo: "neutro". etiquetaSemaforo: el perfil de uso ideal en 2 a 4 palabras (ej. "Ideal para home office").
- puntos: una ventaja clave, una limitación a considerar y un truco de uso o cuidado (duración, compatibilidad, garantía).`,
  curiosidades: `Modo CURIOSIDADES. Cuenta lo más interesante del producto, su historia, su origen o cómo se fabrica.
- semaforo: "neutro". etiquetaSemaforo: una categoría curiosa en 2 a 4 palabras (ej. "Clásico peruano").
- puntos: tres datos interesantes distintos entre sí.`,
};

function describir(producto: ProductQuery): string {
  const partes = [producto.marca, producto.nombre, producto.presentacion].filter((p) => p && p.trim());
  const tipo = producto.tipo ? ` (tipo: ${producto.tipo})` : '';
  return `${partes.join(' ')}${tipo}, categoría "${producto.categoria}"`;
}

export function promptInsights(producto: ProductQuery, modo: Modo): string {
  return `Producto: ${describir(producto)}.\n${POR_MODO[modo]}\n${COMUN}`;
}

export async function generateInsights(producto: ProductQuery, modo: Modo): Promise<ProductInsights> {
  const { output } = await generateText({
    model: MODEL,
    prompt: promptInsights(producto, modo),
    output: Output.object({ schema: ProductInsightsSchema }),
  });
  return output;
}
