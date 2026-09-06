# Backend de Identificación Visual (Plan 1 de 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir el backend en Vercel (Node.js/TypeScript) con un endpoint `POST /api/scan` que recibe una foto y devuelve el producto identificado (marca, nombre, presentación, categoría, confianza) usando IA de visión — sin scraping todavía (eso es el Plan 2).

**Architecture:** Vercel serverless functions bajo `/api`. La lógica de negocio vive en módulos puros bajo `/lib` (validación, llamada a IA, orquestación), y los archivos de `/api` son adaptadores delgados que traducen `req`/`res` de Vercel a esas funciones puras. Esto permite testear toda la lógica con Vitest sin necesitar un servidor HTTP real ni credenciales de IA en los tests automatizados.

**Tech Stack:** TypeScript, Vercel serverless functions (`@vercel/node`), Vercel AI SDK (`ai` v7, vía AI Gateway) con modelo `anthropic/claude-sonnet-5`, Zod para validación de esquemas, Vitest para tests.

## Global Constraints

- Backend en Node.js/TypeScript desplegado en Vercel (del spec: "Vercel (Node.js/TypeScript)").
- Un único endpoint orquestador: `POST /api/scan` (del spec: "Endpoint único `POST /api/scan`").
- La identificación visual debe devolver JSON estructurado con exactamente estos campos: `marca`, `nombre`, `presentacion`, `categoria`, `confianza` (del spec: "IA de visión... devuelve JSON estructurado").
- Si `confianza` es baja, no se debe invocar scraping (eso se implementa en el Plan 2; este plan solo debe dejar el campo `confianza` disponible en la respuesta para que el Plan 2 lo use).
- El cliente Android nunca debe llamar directamente a la IA — solo el backend tiene la API key (del spec: "el cliente Android nunca llama directamente a las tiendas ni a la IA").

---

### Task 1: Scaffolding del proyecto + endpoint de salud

**Files:**
- Create: `package.json`
- Create: `tsconfig.json`
- Create: `lib/health.ts`
- Create: `api/health.ts`
- Test: `test/health.test.ts`

**Interfaces:**
- Consumes: nada (primera tarea).
- Produces: patrón de archivo `lib/<nombre>.ts` (función pura) + `api/<nombre>.ts` (adaptador Vercel) que todas las tareas siguientes replican. Función `getHealthStatus(): { status: string }`.

- [ ] **Step 1: Inicializar el proyecto npm**

```bash
npm init -y
```

- [ ] **Step 2: Instalar dependencias**

```bash
npm install ai zod
npm install -D typescript vitest @types/node @vercel/node
```

- [ ] **Step 3: Configurar `package.json`**

Edita `package.json` para que quede así (ajusta el `name` si ya tiene uno distinto de `npm init`):

```json
{
  "name": "product-price-scanner-backend",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "test": "vitest run",
    "build": "tsc --noEmit"
  },
  "dependencies": {
    "ai": "^7.0.0",
    "zod": "^3.23.8"
  },
  "devDependencies": {
    "@vercel/node": "^3.2.0",
    "@types/node": "^22.0.0",
    "typescript": "^5.6.0",
    "vitest": "^2.1.0"
  }
}
```

- [ ] **Step 4: Crear `tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "resolveJsonModule": true,
    "types": ["node"]
  },
  "include": ["api/**/*.ts", "lib/**/*.ts", "test/**/*.ts"]
}
```

- [ ] **Step 5: Escribir el test que falla**

Crea `test/health.test.ts`:

```ts
import { describe, it, expect } from 'vitest';
import { getHealthStatus } from '../lib/health';

describe('getHealthStatus', () => {
  it('devuelve status ok', () => {
    expect(getHealthStatus()).toEqual({ status: 'ok' });
  });
});
```

- [ ] **Step 6: Ejecutar el test y verificar que falla**

Run: `npx vitest run test/health.test.ts`
Expected: FAIL — no se puede resolver el módulo `../lib/health` (todavía no existe).

- [ ] **Step 7: Implementar `lib/health.ts`**

```ts
export function getHealthStatus(): { status: string } {
  return { status: 'ok' };
}
```

- [ ] **Step 8: Ejecutar el test y verificar que pasa**

Run: `npx vitest run test/health.test.ts`
Expected: PASS (1 test)

- [ ] **Step 9: Crear el adaptador Vercel `api/health.ts`**

```ts
import type { VercelRequest, VercelResponse } from '@vercel/node';
import { getHealthStatus } from '../lib/health';

export default function handler(req: VercelRequest, res: VercelResponse) {
  res.status(200).json(getHealthStatus());
}
```

- [ ] **Step 10: Verificar que el proyecto compila**

Run: `npm run build`
Expected: sin errores de TypeScript.

- [ ] **Step 11: Commit**

```bash
git add package.json tsconfig.json lib/health.ts api/health.ts test/health.test.ts package-lock.json
git commit -m "chore: scaffold backend project with health check endpoint"
```

---

### Task 2: Esquema y tipos de `ProductIdentification`

**Files:**
- Create: `lib/productIdentification.ts`
- Test: `test/productIdentification.test.ts`

**Interfaces:**
- Consumes: nada.
- Produces: `ProductIdentificationSchema` (Zod schema) y el tipo `ProductIdentification` con campos `{ marca: string, nombre: string, presentacion: string, categoria: string, confianza: number }`, usados por las Tasks 3, 4 y 5.

- [ ] **Step 1: Escribir el test que falla**

Crea `test/productIdentification.test.ts`:

```ts
import { describe, it, expect } from 'vitest';
import { ProductIdentificationSchema } from '../lib/productIdentification';

describe('ProductIdentificationSchema', () => {
  it('acepta un objeto válido', () => {
    const result = ProductIdentificationSchema.safeParse({
      marca: 'Gloria',
      nombre: 'Leche evaporada',
      presentacion: '400g',
      categoria: 'abarrotes',
      confianza: 0.92,
    });
    expect(result.success).toBe(true);
  });

  it('rechaza confianza mayor a 1', () => {
    const result = ProductIdentificationSchema.safeParse({
      marca: 'Gloria',
      nombre: 'Leche evaporada',
      presentacion: '400g',
      categoria: 'abarrotes',
      confianza: 1.5,
    });
    expect(result.success).toBe(false);
  });

  it('rechaza confianza negativa', () => {
    const result = ProductIdentificationSchema.safeParse({
      marca: 'Gloria',
      nombre: 'Leche evaporada',
      presentacion: '400g',
      categoria: 'abarrotes',
      confianza: -0.1,
    });
    expect(result.success).toBe(false);
  });

  it('rechaza si falta un campo requerido', () => {
    const result = ProductIdentificationSchema.safeParse({
      marca: 'Gloria',
      nombre: 'Leche evaporada',
      confianza: 0.9,
    });
    expect(result.success).toBe(false);
  });
});
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `npx vitest run test/productIdentification.test.ts`
Expected: FAIL — no se puede resolver `../lib/productIdentification`.

- [ ] **Step 3: Implementar `lib/productIdentification.ts`**

```ts
import { z } from 'zod';

export const ProductIdentificationSchema = z.object({
  marca: z.string(),
  nombre: z.string(),
  presentacion: z.string(),
  categoria: z.string(),
  confianza: z.number().min(0).max(1),
});

export type ProductIdentification = z.infer<typeof ProductIdentificationSchema>;
```

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

Run: `npx vitest run test/productIdentification.test.ts`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add lib/productIdentification.ts test/productIdentification.test.ts
git commit -m "feat: add ProductIdentification schema and type"
```

---

### Task 3: Errores tipados compartidos

**Files:**
- Create: `lib/errors.ts`
- Test: `test/errors.test.ts`

**Interfaces:**
- Consumes: nada.
- Produces: clases `ValidationError` e `IdentificationError` (ambas extienden `Error`, con `.name` propio), usadas por las Tasks 4 y 5.

- [ ] **Step 1: Escribir el test que falla**

Crea `test/errors.test.ts`:

```ts
import { describe, it, expect } from 'vitest';
import { ValidationError, IdentificationError } from '../lib/errors';

describe('errores tipados', () => {
  it('ValidationError tiene name y message correctos', () => {
    const err = new ValidationError('campo inválido');
    expect(err.name).toBe('ValidationError');
    expect(err.message).toBe('campo inválido');
    expect(err).toBeInstanceOf(Error);
  });

  it('IdentificationError tiene name, message y cause correctos', () => {
    const cause = new Error('fallo de red');
    const err = new IdentificationError('no se pudo identificar', cause);
    expect(err.name).toBe('IdentificationError');
    expect(err.message).toBe('no se pudo identificar');
    expect(err.cause).toBe(cause);
    expect(err).toBeInstanceOf(Error);
  });
});
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `npx vitest run test/errors.test.ts`
Expected: FAIL — no se puede resolver `../lib/errors`.

- [ ] **Step 3: Implementar `lib/errors.ts`**

```ts
export class ValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ValidationError';
  }
}

export class IdentificationError extends Error {
  public readonly cause?: unknown;

  constructor(message: string, cause?: unknown) {
    super(message);
    this.name = 'IdentificationError';
    this.cause = cause;
  }
}
```

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

Run: `npx vitest run test/errors.test.ts`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add lib/errors.ts test/errors.test.ts
git commit -m "feat: add ValidationError and IdentificationError types"
```

---

### Task 4: Módulo `identifyProduct` (llamada a la IA de visión)

**Files:**
- Create: `lib/identifyProduct.ts`
- Test: `test/identifyProduct.test.ts`

**Interfaces:**
- Consumes: `ProductIdentificationSchema`, `ProductIdentification` (de `lib/productIdentification.ts`, Task 2).
- Produces: `identifyProduct(imageDataUri: string): Promise<ProductIdentification>`, usada por la Task 5.

- [ ] **Step 1: Escribir el test que falla**

Crea `test/identifyProduct.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';

const generateTextMock = vi.fn();
const objectMock = vi.fn((config: unknown) => ({ __schemaConfig: config }));

vi.mock('ai', () => ({
  generateText: generateTextMock,
  Output: { object: objectMock },
}));

import { identifyProduct } from '../lib/identifyProduct';
import { ProductIdentificationSchema } from '../lib/productIdentification';

const VALID_IDENTIFICATION = {
  marca: 'Gloria',
  nombre: 'Leche evaporada',
  presentacion: '400g',
  categoria: 'abarrotes',
  confianza: 0.92,
};

describe('identifyProduct', () => {
  beforeEach(() => {
    generateTextMock.mockReset();
    objectMock.mockClear();
  });

  it('llama a generateText con el modelo, la imagen y el schema correctos', async () => {
    generateTextMock.mockResolvedValue({ output: VALID_IDENTIFICATION });

    const result = await identifyProduct('data:image/jpeg;base64,ABC123');

    expect(result).toEqual(VALID_IDENTIFICATION);
    expect(objectMock).toHaveBeenCalledWith({ schema: ProductIdentificationSchema });

    const callArgs = generateTextMock.mock.calls[0][0];
    expect(callArgs.model).toBe('anthropic/claude-sonnet-5');
    expect(callArgs.messages).toEqual([
      {
        role: 'user',
        content: [
          { type: 'text', text: expect.any(String) },
          { type: 'image', image: 'data:image/jpeg;base64,ABC123' },
        ],
      },
    ]);
  });

  it('propaga el error si generateText falla', async () => {
    generateTextMock.mockRejectedValue(new Error('timeout de red'));

    await expect(identifyProduct('data:image/jpeg;base64,ABC123')).rejects.toThrow('timeout de red');
  });
});
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `npx vitest run test/identifyProduct.test.ts`
Expected: FAIL — no se puede resolver `../lib/identifyProduct`.

- [ ] **Step 3: Implementar `lib/identifyProduct.ts`**

```ts
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
```

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

Run: `npx vitest run test/identifyProduct.test.ts`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add lib/identifyProduct.ts test/identifyProduct.test.ts
git commit -m "feat: add identifyProduct vision AI module"
```

---

### Task 5: Orquestador `handleScan` (validación + llamada a `identifyProduct`)

**Files:**
- Create: `lib/scanHandler.ts`
- Test: `test/scanHandler.test.ts`

**Interfaces:**
- Consumes: `identifyProduct` (Task 4), `ValidationError`/`IdentificationError` (Task 3), `ProductIdentification` (Task 2).
- Produces: `handleScan(body: unknown): Promise<{ identification: ProductIdentification }>`, usada por la Task 6. Lanza `ValidationError` si el body es inválido, `IdentificationError` si `identifyProduct` falla.

- [ ] **Step 1: Escribir el test que falla**

Crea `test/scanHandler.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';

const identifyProductMock = vi.fn();

vi.mock('../lib/identifyProduct', () => ({
  identifyProduct: identifyProductMock,
}));

import { handleScan } from '../lib/scanHandler';
import { ValidationError, IdentificationError } from '../lib/errors';

const VALID_IDENTIFICATION = {
  marca: 'Gloria',
  nombre: 'Leche evaporada',
  presentacion: '400g',
  categoria: 'abarrotes',
  confianza: 0.92,
};

describe('handleScan', () => {
  beforeEach(() => {
    identifyProductMock.mockReset();
  });

  it('lanza ValidationError si el body no es un objeto', async () => {
    await expect(handleScan(null)).rejects.toThrow(ValidationError);
    await expect(handleScan('texto')).rejects.toThrow(ValidationError);
  });

  it('lanza ValidationError si falta el campo image', async () => {
    await expect(handleScan({})).rejects.toThrow(ValidationError);
  });

  it('lanza ValidationError si image no es una data URI de imagen', async () => {
    await expect(handleScan({ image: 'no-es-una-imagen' })).rejects.toThrow(ValidationError);
  });

  it('devuelve la identificación cuando el body es válido', async () => {
    identifyProductMock.mockResolvedValue(VALID_IDENTIFICATION);

    const result = await handleScan({ image: 'data:image/jpeg;base64,ABC123' });

    expect(result).toEqual({ identification: VALID_IDENTIFICATION });
    expect(identifyProductMock).toHaveBeenCalledWith('data:image/jpeg;base64,ABC123');
  });

  it('lanza IdentificationError si identifyProduct falla', async () => {
    identifyProductMock.mockRejectedValue(new Error('fallo de IA'));

    await expect(handleScan({ image: 'data:image/jpeg;base64,ABC123' })).rejects.toThrow(IdentificationError);
  });
});
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `npx vitest run test/scanHandler.test.ts`
Expected: FAIL — no se puede resolver `../lib/scanHandler`.

- [ ] **Step 3: Implementar `lib/scanHandler.ts`**

```ts
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
```

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

Run: `npx vitest run test/scanHandler.test.ts`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add lib/scanHandler.ts test/scanHandler.test.ts
git commit -m "feat: add handleScan orchestrator with validation"
```

---

### Task 6: Endpoint `POST /api/scan` (adaptador Vercel)

**Files:**
- Create: `api/scan.ts`
- Test: `test/scan.test.ts`

**Interfaces:**
- Consumes: `handleScan` (Task 5), `ValidationError`/`IdentificationError` (Task 3).
- Produces: handler HTTP `POST /api/scan` — 200 con `{ identification }`, 400 en validación, 502 si falla la identificación, 405 si el método no es POST, 500 en cualquier otro error.

- [ ] **Step 1: Escribir el test que falla**

Crea `test/scan.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { VercelRequest, VercelResponse } from '@vercel/node';

const handleScanMock = vi.fn();

vi.mock('../lib/scanHandler', () => ({
  handleScan: handleScanMock,
}));

import handler from '../api/scan';
import { ValidationError, IdentificationError } from '../lib/errors';

function createMockRes() {
  const res = {
    statusCode: 0,
    body: undefined as unknown,
    status(code: number) {
      res.statusCode = code;
      return res;
    },
    json(payload: unknown) {
      res.body = payload;
      return res;
    },
  };
  return res as unknown as VercelResponse & { statusCode: number; body: unknown };
}

function createMockReq(method: string, body: unknown): VercelRequest {
  return { method, body } as VercelRequest;
}

describe('POST /api/scan', () => {
  beforeEach(() => {
    handleScanMock.mockReset();
  });

  it('responde 405 si el método no es POST', async () => {
    const req = createMockReq('GET', {});
    const res = createMockRes();

    await handler(req, res);

    expect(res.statusCode).toBe(405);
  });

  it('responde 200 con la identificación en éxito', async () => {
    const identification = { marca: 'Gloria', nombre: 'Leche', presentacion: '400g', categoria: 'abarrotes', confianza: 0.9 };
    handleScanMock.mockResolvedValue({ identification });
    const req = createMockReq('POST', { image: 'data:image/jpeg;base64,ABC' });
    const res = createMockRes();

    await handler(req, res);

    expect(res.statusCode).toBe(200);
    expect(res.body).toEqual({ identification });
  });

  it('responde 400 si handleScan lanza ValidationError', async () => {
    handleScanMock.mockRejectedValue(new ValidationError('campo inválido'));
    const req = createMockReq('POST', {});
    const res = createMockRes();

    await handler(req, res);

    expect(res.statusCode).toBe(400);
    expect(res.body).toEqual({ error: 'campo inválido' });
  });

  it('responde 502 si handleScan lanza IdentificationError', async () => {
    handleScanMock.mockRejectedValue(new IdentificationError('no se pudo identificar'));
    const req = createMockReq('POST', { image: 'data:image/jpeg;base64,ABC' });
    const res = createMockRes();

    await handler(req, res);

    expect(res.statusCode).toBe(502);
    expect(res.body).toEqual({ error: 'no se pudo identificar' });
  });

  it('responde 500 en cualquier otro error', async () => {
    handleScanMock.mockRejectedValue(new Error('boom'));
    const req = createMockReq('POST', { image: 'data:image/jpeg;base64,ABC' });
    const res = createMockRes();

    await handler(req, res);

    expect(res.statusCode).toBe(500);
    expect(res.body).toEqual({ error: 'Error interno del servidor.' });
  });
});
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `npx vitest run test/scan.test.ts`
Expected: FAIL — no se puede resolver `../api/scan`.

- [ ] **Step 3: Implementar `api/scan.ts`**

```ts
import type { VercelRequest, VercelResponse } from '@vercel/node';
import { handleScan } from '../lib/scanHandler';
import { ValidationError, IdentificationError } from '../lib/errors';

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== 'POST') {
    res.status(405).json({ error: 'Método no permitido. Usa POST.' });
    return;
  }

  try {
    const result = await handleScan(req.body);
    res.status(200).json(result);
  } catch (err) {
    if (err instanceof ValidationError) {
      res.status(400).json({ error: err.message });
      return;
    }
    if (err instanceof IdentificationError) {
      res.status(502).json({ error: err.message });
      return;
    }
    res.status(500).json({ error: 'Error interno del servidor.' });
  }
}
```

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

Run: `npx vitest run test/scan.test.ts`
Expected: PASS (5 tests)

- [ ] **Step 5: Ejecutar toda la suite de tests**

Run: `npm test`
Expected: PASS (todos los tests de las Tasks 1-6, 18 tests en total)

- [ ] **Step 6: Commit**

```bash
git add api/scan.ts test/scan.test.ts
git commit -m "feat: add POST /api/scan endpoint"
```

---

### Task 7: Verificación manual end-to-end con credenciales reales

Esta tarea no tiene test automatizado porque requiere una cuenta de Vercel con AI Gateway habilitado y gasta una llamada real a la IA. Es la única forma de confirmar que el modelo `anthropic/claude-sonnet-5` y el formato de imagen realmente funcionan contra la API real (los tests anteriores mockean la IA).

**Files:** ninguno (solo verificación).

- [ ] **Step 1: Instalar Vercel CLI si no está instalado**

```bash
npm i -g vercel
```

- [ ] **Step 2: Enlazar el proyecto con Vercel**

```bash
vercel link
```

Sigue las instrucciones interactivas para crear o vincular el proyecto.

- [ ] **Step 3: Descargar las variables de entorno (incluye la credencial del AI Gateway)**

```bash
vercel env pull .env.local
```

Expected: se crea `.env.local` con `AI_GATEWAY_API_KEY` (u otra credencial equivalente que Vercel provea para el proyecto).

- [ ] **Step 4: Levantar el servidor de desarrollo**

```bash
vercel dev
```

Expected: el servidor queda escuchando en `http://localhost:3000`.

- [ ] **Step 5: Preparar una imagen de prueba**

Coloca una foto de un producto real (ej. una lata o caja con marca visible) en `test-fixtures/producto-prueba.jpg`. Puedes usar cualquier foto tomada con el celular.

- [ ] **Step 6: Llamar al endpoint con curl**

```bash
curl -X POST http://localhost:3000/api/scan \
  -H "Content-Type: application/json" \
  -d "{\"image\": \"data:image/jpeg;base64,$(base64 -w0 test-fixtures/producto-prueba.jpg)\"}"
```

Expected: respuesta JSON `200` con forma `{"identification": {"marca": "...", "nombre": "...", "presentacion": "...", "categoria": "...", "confianza": <número entre 0 y 1>}}`, y los valores deben corresponder razonablemente al producto de la foto.

- [ ] **Step 7: Probar el caso de imagen inválida**

```bash
curl -X POST http://localhost:3000/api/scan \
  -H "Content-Type: application/json" \
  -d '{"image": "no-es-una-imagen"}'
```

Expected: respuesta `400` con `{"error": "..."}`.

- [ ] **Step 8: Documentar el resultado**

Si el modelo o el formato de imagen no funcionaron como se esperaba, anota el error real de la API y ajusta `lib/identifyProduct.ts` (por ejemplo, si el nombre del modelo cambió o el SDK espera otro formato de imagen). Vuelve a correr `npm test` después de cualquier ajuste para confirmar que los tests mockeados siguen pasando.

- [ ] **Step 9: Commit final si hubo ajustes**

```bash
git add -A
git commit -m "fix: adjust identifyProduct after live verification"
```

(Omite este paso si el Step 6 funcionó sin cambios.)
