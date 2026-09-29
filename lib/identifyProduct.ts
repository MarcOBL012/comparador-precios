import { generateText, Output } from 'ai';
import { google } from '@ai-sdk/google';
import { ProductIdentificationSchema, type Categoria, type ProductIdentification } from './productIdentification.js';

// Gemini Flash: rápido, barato y con visión. Lee la key de GOOGLE_GENERATIVE_AI_API_KEY.
// (Antes: gateway de Vercel 'anthropic/claude-sonnet-5', que exige tarjeta en el proyecto.)
const MODEL = google('gemini-3.6-flash');

const IDENTIFICATION_PROMPT = `Eres un asistente que identifica productos de supermercado o retail a partir de una foto de su empaque o etiqueta, para buscar su precio en tiendas peruanas.
Analiza la imagen con cuidado, incluso si el texto es pequeño o la marca es local/regional y no mundialmente famosa (una marca peruana poco conocida es tan válida como una internacional: transcribe lo que ves, no lo que reconoces). Devuelve:
- marca: la marca tal como está impresa en el empaque, letra por letra. No la sustituyas por una marca más conocida ni la traduzcas.
- nombre: el nombre del producto (sin la marca), incluyendo cualquier palabra visible que lo distinga de otras variantes de la misma marca: sabor, color, línea/edición o código de modelo (ej. "Maracuyá", "Zero azúcar", "Inalámbrico M190", "Rojo talla M"). Esto es lo que se usa para buscarlo, así que entre más específico mejor.
- presentacion: tamaño, peso, volumen o capacidad (ej. "500ml", "1kg", "talla M"), o "" si no es visible.
- categoria: exactamente una de estas:
  "abarrotes" (alimentos envasados, lácteos, snacks), "bebidas", "limpieza" (del hogar),
  "cuidado_personal" (higiene, cosmética, farmacia), "tecnologia" (cómputo, celulares, audio, videojuegos),
  "electrohogar" (electrodomésticos), "hogar" (muebles, decoración, cocina), "ferreteria" (herramientas, construcción),
  "moda" (ropa, calzado, accesorios), "juguetes", "otros".
- tipo: qué producto es, en 1 o 2 palabras en español, sin marca, modelo ni variante: lo mínimo que lo distingue de productos parecidos. Ej. "leche evaporada" (no solo "leche", porque la leche UHT es otro producto), "mouse", "audífonos", "licuadora", "detergente" (no "detergente en polvo").
- confianza: un número entre 0 y 1. Bájalo SOLO por problemas de la propia foto (borrosa, mal enfocada, empaque tapado, reflejo o ángulo que no deja leer el texto). Nunca lo bajes solo porque la marca o el producto te resulten desconocidos o poco comunes: si el texto se lee con claridad, transcríbelo tal cual y usa confianza alta aunque nunca hayas oído de esa marca antes.
Si de verdad no puedes leer nada del empaque, usa confianza 0, categoria "otros" y deja marca, nombre, presentacion y tipo como cadenas vacías.`;

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
