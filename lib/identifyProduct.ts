import { generateText, Output } from 'ai';
import { google } from '@ai-sdk/google';
import { ProductIdentificationSchema, type Categoria, type ProductIdentification } from './productIdentification.js';

// Gemini Flash: rápido, barato y con visión. Lee la key de GOOGLE_GENERATIVE_AI_API_KEY.
// (Antes: gateway de Vercel 'anthropic/claude-sonnet-5', que exige tarjeta en el proyecto.)
const MODEL = google('gemini-3.6-flash');

const IDENTIFICATION_PROMPT = `Eres un asistente que identifica productos de supermercado o retail a partir de una foto de su empaque o etiqueta.
Analiza la imagen y devuelve:
- marca: la marca del producto tal como aparece en el empaque.
- nombre: el nombre del producto (sin la marca).
- presentacion: tamaño, peso, volumen o variante (ej. "500ml", "1kg", "talla M"), o "" si no es visible.
- categoria: exactamente una de estas:
  "abarrotes" (alimentos envasados, lácteos, snacks), "bebidas", "limpieza" (del hogar),
  "cuidado_personal" (higiene, cosmética, farmacia), "tecnologia" (cómputo, celulares, audio, videojuegos),
  "electrohogar" (electrodomésticos), "hogar" (muebles, decoración, cocina), "ferreteria" (herramientas, construcción),
  "moda" (ropa, calzado, accesorios), "juguetes", "otros".
- tipo: qué producto es, en 1 o 2 palabras en español, sin marca, modelo ni variante: lo mínimo que lo distingue de productos parecidos. Ej. "leche evaporada" (no solo "leche", porque la leche UHT es otro producto), "mouse", "audífonos", "licuadora", "detergente" (no "detergente en polvo").
- confianza: un número entre 0 y 1 que indica qué tan seguro estás de la identificación. Usa un valor menor a 0.5 si la imagen es borrosa, el empaque no es claramente visible, o no puedes leer marca/nombre con certeza.
Si no puedes identificar el producto en absoluto, usa confianza 0, categoria "otros" y deja marca, nombre, presentacion y tipo como cadenas vacías.`;

function promptFor(chosenCategory: Categoria | null): string {
  if (!chosenCategory) {
    return IDENTIFICATION_PROMPT;
  }
  return `${IDENTIFICATION_PROMPT}
Antes de tomar la foto, la persona indicó que el producto es de la categoría "${chosenCategory}". Úsalo como pista para leer el empaque, pero si la foto muestra claramente un producto de otra categoría, devuelve la categoría correcta.`;
}

export async function identifyProduct(
  imageDataUri: string,
  chosenCategory: Categoria | null = null
): Promise<ProductIdentification> {
  const { output } = await generateText({
    model: MODEL,
    messages: [
      {
        role: 'user',
        content: [
          { type: 'text', text: promptFor(chosenCategory) },
          { type: 'image', image: imageDataUri },
        ],
      },
    ],
    output: Output.object({ schema: ProductIdentificationSchema }),
  });
  return output;
}
