import { generateText, Output } from 'ai';
import { ProductIdentificationSchema, type ProductIdentification } from './productIdentification';

const MODEL = 'anthropic/claude-sonnet-5';

const IDENTIFICATION_PROMPT = `Eres un asistente que identifica productos de supermercado o retail a partir de una foto de su empaque o etiqueta.
Analiza la imagen y devuelve:
- marca: la marca del producto tal como aparece en el empaque.
- nombre: el nombre del producto (sin la marca).
- presentacion: tamaño, peso, volumen o variante (ej. "500ml", "1kg", "talla M"), o "" si no es visible.
- categoria: una categoría general breve (ej. "abarrotes", "bebidas", "electrodomésticos", "ropa").
- confianza: un número entre 0 y 1 que indica qué tan seguro estás de la identificación. Usa un valor menor a 0.5 si la imagen es borrosa, el empaque no es claramente visible, o no puedes leer marca/nombre con certeza.
Si no puedes identificar el producto en absoluto, usa confianza 0 y deja marca, nombre, presentacion y categoria como cadenas vacías.`;

export async function identifyProduct(imageDataUri: string): Promise<ProductIdentification> {
  const { output } = await generateText({
    model: MODEL,
    messages: [
      {
        role: 'user',
        content: [
          { type: 'text', text: IDENTIFICATION_PROMPT },
          { type: 'image', image: imageDataUri },
        ],
      },
    ],
    output: Output.object({ schema: ProductIdentificationSchema }),
  });
  return output;
}
