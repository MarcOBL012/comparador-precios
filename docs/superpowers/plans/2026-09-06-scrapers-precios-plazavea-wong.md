# Scrapers de precios: Plaza Vea + Wong (Plan 2 de 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extender `POST /api/scan` (del Plan 1, ya en `master`) para que, además de identificar el producto, busque su precio en Plaza Vea y Wong en tiempo real y devuelva una comparación agregada con estado explícito por tienda.

**Architecture:** Un módulo de matching difuso (`lib/matching.ts`) compara el producto identificado contra los resultados de búsqueda de cada tienda. Un módulo compartido (`lib/stores/vtexSearch.ts`) implementa la búsqueda contra la API pública de VTEX (la plataforma de e-commerce que usan ambas tiendas), y dos módulos delgados (`lib/stores/plazaVea.ts`, `lib/stores/wong.ts`) lo configuran para cada tienda. `lib/scanHandler.ts` (del Plan 1) se extiende para: si la confianza de la identificación es suficiente, ejecutar ambas búsquedas en paralelo, cada una con su propio timeout, y agregar los resultados con estado `encontrado` / `no_encontrado` / `error` por tienda.

**Tech Stack:** TypeScript, `fetch` nativo de Node (con `AbortController` para timeouts), Vitest para tests, `tsx` para el script de verificación manual.

## Global Constraints

- **Alcance de este plan: SOLO Plaza Vea y Wong.** Se investigó en vivo antes de escribir este plan: Plaza Vea y Wong exponen una API JSON pública de VTEX sin autenticación; Falabella no tiene una API verificable (endpoints probados devolvieron errores) y Ripley bloquea peticiones simples con `403 Forbidden` (protección anti-bots activa). Falabella y Ripley quedan diferidos a un Plan 2b futuro — no se debe intentar implementarlos en este plan.
- API verificada en vivo para ambas tiendas: `GET {baseUrl}/api/catalog_system/pub/products/search/{término}?_from=0&_to=9` devuelve un array JSON de productos, cada uno con `productName`, `brand`, `link`, e `items[0].sellers[0].commertialOffer.{Price, IsAvailable}`.
- Scrapers por tienda: un módulo independiente por tienda con interfaz común, cada uno con su propio timeout, para que una tienda lenta o caída no bloquee la respuesta de la otra (del spec).
- Matching difuso: se descarta el candidato si la similitud es insuficiente, en vez de forzar un match incorrecto (del spec).
- Agregación: estado explícito por tienda, con estos valores exactos: `encontrado`, `no_encontrado`, `error` (del spec).
- Si la confianza de la identificación es menor a `0.5`, no se ejecutan búsquedas en tiendas (umbral heredado del prompt de identificación del Plan 1, que usa 0.5 como corte de confianza baja).
- El campo `confianza` y el resto de la identificación no cambian de forma en este plan — este plan solo añade el campo `tiendas` a la respuesta de `POST /api/scan`.

---

### Task 1: Módulo de matching difuso

**Files:**
- Create: `lib/matching.ts`
- Test: `test/matching.test.ts`

**Interfaces:**
- Consumes: `ProductIdentification` (de `lib/productIdentification.ts`, Plan 1).
- Produces: `MatchCandidate` (interfaz `{ productName: string; brand: string }`), `MATCH_THRESHOLD` (constante `0.3`), y `pickBestMatch<T extends MatchCandidate>(identification: ProductIdentification, candidates: T[]): T | null` — usada por la Task 2.

- [ ] **Step 1: Escribir el test que falla**

Crea `test/matching.test.ts`:

```ts
import { describe, it, expect } from 'vitest';
import { pickBestMatch } from '../lib/matching';
import type { ProductIdentification } from '../lib/productIdentification';

const identification: ProductIdentification = {
  marca: 'Gloria',
  nombre: 'Leche evaporada',
  presentacion: '400g',
  categoria: 'abarrotes',
  confianza: 0.9,
};

describe('pickBestMatch', () => {
  it('elige el candidato con mayor similitud por encima del umbral', () => {
    const candidates = [
      { productName: 'Leche Evaporada Ideal 400g', brand: 'Ideal' },
      { productName: 'Leche Evaporada Gloria 400g', brand: 'Gloria' },
    ];

    const result = pickBestMatch(identification, candidates);

    expect(result).toEqual(candidates[1]);
  });

  it('devuelve null si ningún candidato supera el umbral', () => {
    const candidates = [{ productName: 'Detergente Ariel 800g', brand: 'Ariel' }];

    expect(pickBestMatch(identification, candidates)).toBeNull();
  });

  it('devuelve null si no hay candidatos', () => {
    expect(pickBestMatch(identification, [])).toBeNull();
  });

  it('ignora mayúsculas y acentos al comparar', () => {
    const candidates = [{ productName: 'LECHE EVAPORADA GLÓRIA 400G', brand: 'GLORIA' }];

    const result = pickBestMatch(identification, candidates);

    expect(result).toEqual(candidates[0]);
  });

  it('conserva campos extra del candidato en el resultado', () => {
    const candidates = [
      { productName: 'Leche Evaporada Gloria 400g', brand: 'Gloria', precio: 4.5, url: 'https://example.pe/p/1' },
    ];

    const result = pickBestMatch(identification, candidates);

    expect(result).toEqual(candidates[0]);
  });
});
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `npx vitest run test/matching.test.ts`
Expected: FAIL — no se puede resolver `../lib/matching`.

- [ ] **Step 3: Implementar `lib/matching.ts`**

```ts
import type { ProductIdentification } from './productIdentification';

export interface MatchCandidate {
  productName: string;
  brand: string;
}

export const MATCH_THRESHOLD = 0.3;

const ACCENTS: Record<string, string> = {
  á: 'a',
  é: 'e',
  í: 'i',
  ó: 'o',
  ú: 'u',
  ü: 'u',
  ñ: 'n',
};

function normalize(text: string): string {
  return text
    .toLowerCase()
    .replace(/[áéíóúüñ]/g, (char) => ACCENTS[char] ?? char)
    .replace(/[^a-z0-9\s]/g, ' ')
    .split(/\s+/)
    .filter(Boolean)
    .join(' ');
}

function tokenSetSimilarity(a: string, b: string): number {
  const tokensA = new Set(normalize(a).split(' ').filter(Boolean));
  const tokensB = new Set(normalize(b).split(' ').filter(Boolean));
  if (tokensA.size === 0 || tokensB.size === 0) {
    return 0;
  }
  let intersection = 0;
  for (const token of tokensA) {
    if (tokensB.has(token)) {
      intersection++;
    }
  }
  const union = new Set([...tokensA, ...tokensB]).size;
  return intersection / union;
}

function scoreCandidate(identification: ProductIdentification, candidate: MatchCandidate): number {
  const identificationText = `${identification.marca} ${identification.nombre} ${identification.presentacion}`;
  const candidateText = `${candidate.brand} ${candidate.productName}`;
  return tokenSetSimilarity(identificationText, candidateText);
}

export function pickBestMatch<T extends MatchCandidate>(
  identification: ProductIdentification,
  candidates: T[]
): T | null {
  let best: T | null = null;
  let bestScore = 0;
  for (const candidate of candidates) {
    const score = scoreCandidate(identification, candidate);
    if (score > bestScore) {
      bestScore = score;
      best = candidate;
    }
  }
  return bestScore >= MATCH_THRESHOLD ? best : null;
}
```

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

Run: `npx vitest run test/matching.test.ts`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add lib/matching.ts test/matching.test.ts
git commit -m "feat: add fuzzy product matching module"
```

---

### Task 2: Búsqueda compartida contra la API de VTEX

**Files:**
- Create: `lib/stores/types.ts`
- Create: `lib/stores/vtexSearch.ts`
- Test: `test/stores/vtexSearch.test.ts`

**Interfaces:**
- Consumes: `pickBestMatch`, `MatchCandidate` (de `lib/matching.ts`, Task 1); `ProductIdentification` (Plan 1).
- Produces: `StoreProduct` (interfaz `{ producto: string; precio: number; url: string }`), `VtexStoreConfig` (interfaz `{ baseUrl: string; storeName: string; timeoutMs: number }`), y `searchVtexStore(config: VtexStoreConfig, identification: ProductIdentification): Promise<StoreProduct | null>` — usada por la Task 3.

- [ ] **Step 1: Escribir el test que falla**

Crea `test/stores/vtexSearch.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { searchVtexStore } from '../../lib/stores/vtexSearch';
import type { ProductIdentification } from '../../lib/productIdentification';

const identification: ProductIdentification = {
  marca: 'Gloria',
  nombre: 'Leche evaporada',
  presentacion: '400g',
  categoria: 'abarrotes',
  confianza: 0.9,
};

const CONFIG = { baseUrl: 'https://www.example-store.pe', storeName: 'Tienda Ejemplo', timeoutMs: 5000 };

function jsonResponse(body: unknown, ok = true, status = 200) {
  return {
    ok,
    status,
    json: async () => body,
  } as Response;
}

describe('searchVtexStore', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('construye la URL de búsqueda con la marca y el nombre codificados', async () => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    await searchVtexStore(CONFIG, identification);

    expect(fetchMock).toHaveBeenCalledWith(
      'https://www.example-store.pe/api/catalog_system/pub/products/search/Gloria%20Leche%20evaporada?_from=0&_to=9',
      expect.objectContaining({ signal: expect.any(AbortSignal) })
    );
  });

  it('devuelve null si la tienda no tiene resultados', async () => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    const result = await searchVtexStore(CONFIG, identification);

    expect(result).toBeNull();
  });

  it('devuelve null si el único resultado no está disponible', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse([
        {
          productName: 'Leche Evaporada Gloria 400g',
          brand: 'Gloria',
          link: 'https://www.example-store.pe/p/1',
          items: [{ sellers: [{ commertialOffer: { Price: 4.5, IsAvailable: false } }] }],
        },
      ])
    );

    const result = await searchVtexStore(CONFIG, identification);

    expect(result).toBeNull();
  });

  it('devuelve el mejor candidato disponible mapeado a producto/precio/url', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse([
        {
          productName: 'Detergente Ariel 800g',
          brand: 'Ariel',
          link: 'https://www.example-store.pe/p/0',
          items: [{ sellers: [{ commertialOffer: { Price: 12.9, IsAvailable: true } }] }],
        },
        {
          productName: 'Leche Evaporada Gloria 400g',
          brand: 'Gloria',
          link: 'https://www.example-store.pe/p/1',
          items: [{ sellers: [{ commertialOffer: { Price: 4.5, IsAvailable: true } }] }],
        },
      ])
    );

    const result = await searchVtexStore(CONFIG, identification);

    expect(result).toEqual({
      producto: 'Leche Evaporada Gloria 400g',
      precio: 4.5,
      url: 'https://www.example-store.pe/p/1',
    });
  });

  it('lanza un error descriptivo si la respuesta no es exitosa', async () => {
    fetchMock.mockResolvedValue(jsonResponse(null, false, 500));

    await expect(searchVtexStore(CONFIG, identification)).rejects.toThrow('Tienda Ejemplo respondió con estado 500');
  });
});
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `npx vitest run test/stores/vtexSearch.test.ts`
Expected: FAIL — no se puede resolver `../../lib/stores/vtexSearch`.

- [ ] **Step 3: Implementar `lib/stores/types.ts`**

```ts
export interface StoreProduct {
  producto: string;
  precio: number;
  url: string;
}
```

- [ ] **Step 4: Implementar `lib/stores/vtexSearch.ts`**

```ts
import { pickBestMatch, type MatchCandidate } from '../matching';
import type { ProductIdentification } from '../productIdentification';
import type { StoreProduct } from './types';

interface VtexProduct {
  productName: string;
  brand: string;
  link: string;
  items?: Array<{
    sellers?: Array<{
      commertialOffer?: {
        Price: number;
        IsAvailable: boolean;
      };
    }>;
  }>;
}

interface VtexCandidate extends MatchCandidate {
  precio: number;
  url: string;
}

export interface VtexStoreConfig {
  baseUrl: string;
  storeName: string;
  timeoutMs: number;
}

export async function searchVtexStore(
  config: VtexStoreConfig,
  identification: ProductIdentification
): Promise<StoreProduct | null> {
  const term = `${identification.marca} ${identification.nombre}`.trim();
  const url = `${config.baseUrl}/api/catalog_system/pub/products/search/${encodeURIComponent(term)}?_from=0&_to=9`;

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), config.timeoutMs);

  let products: VtexProduct[];
  try {
    const response = await fetch(url, { signal: controller.signal });
    if (!response.ok) {
      throw new Error(`${config.storeName} respondió con estado ${response.status}`);
    }
    products = (await response.json()) as VtexProduct[];
  } finally {
    clearTimeout(timeout);
  }

  const candidates: VtexCandidate[] = [];
  for (const product of products) {
    const offer = product.items?.[0]?.sellers?.[0]?.commertialOffer;
    if (offer && offer.IsAvailable && offer.Price > 0) {
      candidates.push({
        productName: product.productName,
        brand: product.brand,
        precio: offer.Price,
        url: product.link,
      });
    }
  }

  const best = pickBestMatch(identification, candidates);
  if (!best) {
    return null;
  }
  return { producto: best.productName, precio: best.precio, url: best.url };
}
```

- [ ] **Step 5: Ejecutar el test y verificar que pasa**

Run: `npx vitest run test/stores/vtexSearch.test.ts`
Expected: PASS (5 tests)

- [ ] **Step 6: Commit**

```bash
git add lib/stores/types.ts lib/stores/vtexSearch.ts test/stores/vtexSearch.test.ts
git commit -m "feat: add shared VTEX store search module"
```

---

### Task 3: Módulos de Plaza Vea y Wong

**Files:**
- Create: `lib/stores/plazaVea.ts`
- Create: `lib/stores/wong.ts`
- Test: `test/stores/plazaVea.test.ts`
- Test: `test/stores/wong.test.ts`

**Interfaces:**
- Consumes: `searchVtexStore`, `StoreProduct` (de `lib/stores/vtexSearch.ts` y `lib/stores/types.ts`, Task 2).
- Produces: `buscarPlazaVea(identification: ProductIdentification): Promise<StoreProduct | null>` y `buscarWong(identification: ProductIdentification): Promise<StoreProduct | null>` — usadas por la Task 4.

- [ ] **Step 1: Escribir los tests que fallan**

Crea `test/stores/plazaVea.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';

const { searchVtexStoreMock } = vi.hoisted(() => ({
  searchVtexStoreMock: vi.fn(),
}));

vi.mock('../../lib/stores/vtexSearch', () => ({
  searchVtexStore: searchVtexStoreMock,
}));

import { buscarPlazaVea } from '../../lib/stores/plazaVea';
import type { ProductIdentification } from '../../lib/productIdentification';

const identification: ProductIdentification = {
  marca: 'Gloria',
  nombre: 'Leche evaporada',
  presentacion: '400g',
  categoria: 'abarrotes',
  confianza: 0.9,
};

describe('buscarPlazaVea', () => {
  beforeEach(() => {
    searchVtexStoreMock.mockReset();
  });

  it('llama a searchVtexStore con la configuración de Plaza Vea', async () => {
    searchVtexStoreMock.mockResolvedValue(null);

    await buscarPlazaVea(identification);

    expect(searchVtexStoreMock).toHaveBeenCalledWith(
      { baseUrl: 'https://www.plazavea.com.pe', storeName: 'Plaza Vea', timeoutMs: 5000 },
      identification
    );
  });

  it('devuelve lo que resuelve searchVtexStore', async () => {
    const match = { producto: 'Leche Evaporada Gloria 400g', precio: 4.5, url: 'https://www.plazavea.com.pe/p/1' };
    searchVtexStoreMock.mockResolvedValue(match);

    const result = await buscarPlazaVea(identification);

    expect(result).toEqual(match);
  });
});
```

Crea `test/stores/wong.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';

const { searchVtexStoreMock } = vi.hoisted(() => ({
  searchVtexStoreMock: vi.fn(),
}));

vi.mock('../../lib/stores/vtexSearch', () => ({
  searchVtexStore: searchVtexStoreMock,
}));

import { buscarWong } from '../../lib/stores/wong';
import type { ProductIdentification } from '../../lib/productIdentification';

const identification: ProductIdentification = {
  marca: 'Gloria',
  nombre: 'Leche evaporada',
  presentacion: '400g',
  categoria: 'abarrotes',
  confianza: 0.9,
};

describe('buscarWong', () => {
  beforeEach(() => {
    searchVtexStoreMock.mockReset();
  });

  it('llama a searchVtexStore con la configuración de Wong', async () => {
    searchVtexStoreMock.mockResolvedValue(null);

    await buscarWong(identification);

    expect(searchVtexStoreMock).toHaveBeenCalledWith(
      { baseUrl: 'https://www.wong.pe', storeName: 'Wong', timeoutMs: 5000 },
      identification
    );
  });

  it('devuelve lo que resuelve searchVtexStore', async () => {
    const match = { producto: 'Leche Evaporada Gloria 400g', precio: 4.6, url: 'https://www.wong.pe/p/1' };
    searchVtexStoreMock.mockResolvedValue(match);

    const result = await buscarWong(identification);

    expect(result).toEqual(match);
  });
});
```

- [ ] **Step 2: Ejecutar los tests y verificar que fallan**

Run: `npx vitest run test/stores/plazaVea.test.ts test/stores/wong.test.ts`
Expected: FAIL — no se pueden resolver `../../lib/stores/plazaVea` ni `../../lib/stores/wong`.

- [ ] **Step 3: Implementar `lib/stores/plazaVea.ts`**

```ts
import { searchVtexStore } from './vtexSearch';
import type { ProductIdentification } from '../productIdentification';
import type { StoreProduct } from './types';

export function buscarPlazaVea(identification: ProductIdentification): Promise<StoreProduct | null> {
  return searchVtexStore(
    { baseUrl: 'https://www.plazavea.com.pe', storeName: 'Plaza Vea', timeoutMs: 5000 },
    identification
  );
}
```

- [ ] **Step 4: Implementar `lib/stores/wong.ts`**

```ts
import { searchVtexStore } from './vtexSearch';
import type { ProductIdentification } from '../productIdentification';
import type { StoreProduct } from './types';

export function buscarWong(identification: ProductIdentification): Promise<StoreProduct | null> {
  return searchVtexStore(
    { baseUrl: 'https://www.wong.pe', storeName: 'Wong', timeoutMs: 5000 },
    identification
  );
}
```

- [ ] **Step 5: Ejecutar los tests y verificar que pasan**

Run: `npx vitest run test/stores/plazaVea.test.ts test/stores/wong.test.ts`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add lib/stores/plazaVea.ts lib/stores/wong.ts test/stores/plazaVea.test.ts test/stores/wong.test.ts
git commit -m "feat: add Plaza Vea and Wong store search modules"
```

---

### Task 4: Integrar la búsqueda de tiendas en `handleScan`

**Files:**
- Modify: `lib/scanHandler.ts` (reemplazo completo del contenido — ver Step 3)
- Modify: `test/scanHandler.test.ts` (reemplazo completo del contenido — ver Step 1)
- Modify: `test/scanIntegration.test.ts` (reemplazo completo del contenido — ver Step 1)

**Interfaces:**
- Consumes: `buscarPlazaVea`, `buscarWong` (Task 3); `identifyProduct`, `ValidationError`, `IdentificationError`, `ProductIdentification` (Plan 1).
- Produces: `StoreResult` (interfaz `{ tienda: string; estado: 'encontrado' | 'no_encontrado' | 'error'; producto?: string; precio?: number; url?: string; mensaje?: string }`), `ScanResponseBody` actualizado a `{ identification: ProductIdentification; tiendas: StoreResult[] }` — es la forma final de la respuesta de `POST /api/scan` para este plan (consumida por el Plan 3, la app Android).

Este archivo actualmente (al final del Plan 1) valida la imagen, llama a `identifyProduct`, y devuelve `{ identification }`. Esta tarea añade: un umbral de confianza que salta la búsqueda en tiendas si es baja, y la búsqueda paralela en Plaza Vea y Wong (cada una envuelta para que un error en una no tumbe a la otra) cuando la confianza es suficiente.

- [ ] **Step 1: Reemplazar los tests con la versión que falla**

Reemplaza el contenido completo de `test/scanHandler.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';

const { identifyProductMock } = vi.hoisted(() => ({
  identifyProductMock: vi.fn(),
}));
const { buscarPlazaVeaMock } = vi.hoisted(() => ({
  buscarPlazaVeaMock: vi.fn(),
}));
const { buscarWongMock } = vi.hoisted(() => ({
  buscarWongMock: vi.fn(),
}));

vi.mock('../lib/identifyProduct', () => ({
  identifyProduct: identifyProductMock,
}));
vi.mock('../lib/stores/plazaVea', () => ({
  buscarPlazaVea: buscarPlazaVeaMock,
}));
vi.mock('../lib/stores/wong', () => ({
  buscarWong: buscarWongMock,
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

const LOW_CONFIDENCE_IDENTIFICATION = {
  ...VALID_IDENTIFICATION,
  confianza: 0.3,
};

describe('handleScan', () => {
  beforeEach(() => {
    identifyProductMock.mockReset();
    buscarPlazaVeaMock.mockReset();
    buscarWongMock.mockReset();
    buscarPlazaVeaMock.mockResolvedValue(null);
    buscarWongMock.mockResolvedValue(null);
  });

  it('lanza ValidationError si el body no es un objeto', async () => {
    await expect(handleScan(null)).rejects.toThrow(ValidationError);
    await expect(handleScan('texto')).rejects.toThrow(ValidationError);
  });

  it('lanza ValidationError si falta el campo image', async () => {
    await expect(handleScan({})).rejects.toThrow(ValidationError);
  });

  it('lanza ValidationError si image no es una data URI de imagen soportada', async () => {
    await expect(handleScan({ image: 'no-es-una-imagen' })).rejects.toThrow(ValidationError);
  });

  it('rechaza un tipo de imagen no soportado', async () => {
    await expect(handleScan({ image: 'data:image/tiff;base64,ABC123' })).rejects.toThrow(ValidationError);
  });

  it('rechaza una data URI con payload base64 vacío', async () => {
    await expect(handleScan({ image: 'data:image/jpeg;base64,' })).rejects.toThrow(ValidationError);
  });

  it('rechaza una imagen que excede el tamaño máximo', async () => {
    const oversized = 'data:image/jpeg;base64,' + 'A'.repeat(4_000_001);
    await expect(handleScan({ image: oversized })).rejects.toThrow(ValidationError);
  });

  it('lanza IdentificationError si identifyProduct falla', async () => {
    identifyProductMock.mockRejectedValue(new Error('fallo de IA'));
    await expect(handleScan({ image: 'data:image/jpeg;base64,ABC123' })).rejects.toThrow(IdentificationError);
  });

  it('no busca en las tiendas si la confianza es baja, y devuelve tiendas vacío', async () => {
    identifyProductMock.mockResolvedValue(LOW_CONFIDENCE_IDENTIFICATION);

    const result = await handleScan({ image: 'data:image/jpeg;base64,ABC123' });

    expect(result).toEqual({ identification: LOW_CONFIDENCE_IDENTIFICATION, tiendas: [] });
    expect(buscarPlazaVeaMock).not.toHaveBeenCalled();
    expect(buscarWongMock).not.toHaveBeenCalled();
  });

  it('busca en las tiendas cuando la confianza es exactamente 0.5 (el umbral)', async () => {
    identifyProductMock.mockResolvedValue({ ...VALID_IDENTIFICATION, confianza: 0.5 });

    await handleScan({ image: 'data:image/jpeg;base64,ABC123' });

    expect(buscarPlazaVeaMock).toHaveBeenCalled();
    expect(buscarWongMock).toHaveBeenCalled();
  });

  it('busca en ambas tiendas y agrega los resultados cuando la confianza es suficiente', async () => {
    identifyProductMock.mockResolvedValue(VALID_IDENTIFICATION);
    buscarPlazaVeaMock.mockResolvedValue({
      producto: 'Leche Evaporada Gloria 400g',
      precio: 4.5,
      url: 'https://www.plazavea.com.pe/p/1',
    });
    buscarWongMock.mockResolvedValue(null);

    const result = await handleScan({ image: 'data:image/jpeg;base64,ABC123' });

    expect(result).toEqual({
      identification: VALID_IDENTIFICATION,
      tiendas: [
        {
          tienda: 'Plaza Vea',
          estado: 'encontrado',
          producto: 'Leche Evaporada Gloria 400g',
          precio: 4.5,
          url: 'https://www.plazavea.com.pe/p/1',
        },
        { tienda: 'Wong', estado: 'no_encontrado' },
      ],
    });
  });

  it('reporta estado error para una tienda que falla, sin afectar a la otra', async () => {
    identifyProductMock.mockResolvedValue(VALID_IDENTIFICATION);
    buscarPlazaVeaMock.mockRejectedValue(new Error('timeout de red'));
    buscarWongMock.mockResolvedValue({
      producto: 'Leche Evaporada Gloria 400g',
      precio: 4.6,
      url: 'https://www.wong.pe/p/1',
    });

    const result = await handleScan({ image: 'data:image/jpeg;base64,ABC123' });

    expect(result).toEqual({
      identification: VALID_IDENTIFICATION,
      tiendas: [
        { tienda: 'Plaza Vea', estado: 'error', mensaje: 'timeout de red' },
        {
          tienda: 'Wong',
          estado: 'encontrado',
          producto: 'Leche Evaporada Gloria 400g',
          precio: 4.6,
          url: 'https://www.wong.pe/p/1',
        },
      ],
    });
  });
});
```

Reemplaza el contenido completo de `test/scanIntegration.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';

const { generateTextMock, objectMock } = vi.hoisted(() => ({
  generateTextMock: vi.fn(),
  objectMock: vi.fn((config: unknown) => ({ __schemaConfig: config })),
}));
const { buscarPlazaVeaMock } = vi.hoisted(() => ({
  buscarPlazaVeaMock: vi.fn(),
}));
const { buscarWongMock } = vi.hoisted(() => ({
  buscarWongMock: vi.fn(),
}));

vi.mock('ai', () => ({
  generateText: generateTextMock,
  Output: { object: objectMock },
}));
vi.mock('../lib/stores/plazaVea', () => ({
  buscarPlazaVea: buscarPlazaVeaMock,
}));
vi.mock('../lib/stores/wong', () => ({
  buscarWong: buscarWongMock,
}));

import handler from '../api/scan';
import type { VercelRequest, VercelResponse } from '@vercel/node';

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

describe('POST /api/scan (integración: scanHandler e identifyProduct reales)', () => {
  beforeEach(() => {
    generateTextMock.mockReset();
    buscarPlazaVeaMock.mockReset();
    buscarWongMock.mockReset();
    buscarPlazaVeaMock.mockResolvedValue(null);
    buscarWongMock.mockResolvedValue(null);
  });

  it('responde 400 cuando la imagen no es una data URI válida, sin llamar a la IA', async () => {
    const req = createMockReq('POST', { image: 'no-es-una-imagen' });
    const res = createMockRes();

    await handler(req, res);

    expect(res.statusCode).toBe(400);
    expect(generateTextMock).not.toHaveBeenCalled();
  });

  it('responde 200 con la identificación y las tiendas cuando la imagen es válida y la IA responde correctamente', async () => {
    const identification = {
      marca: 'Gloria',
      nombre: 'Leche evaporada',
      presentacion: '400g',
      categoria: 'abarrotes',
      confianza: 0.9,
    };
    generateTextMock.mockResolvedValue({ output: identification });
    buscarPlazaVeaMock.mockResolvedValue({
      producto: 'Leche Evaporada Gloria 400g',
      precio: 4.5,
      url: 'https://www.plazavea.com.pe/p/1',
    });

    const req = createMockReq('POST', { image: 'data:image/jpeg;base64,ABC123' });
    const res = createMockRes();

    await handler(req, res);

    expect(res.statusCode).toBe(200);
    expect(res.body).toEqual({
      identification,
      tiendas: [
        {
          tienda: 'Plaza Vea',
          estado: 'encontrado',
          producto: 'Leche Evaporada Gloria 400g',
          precio: 4.5,
          url: 'https://www.plazavea.com.pe/p/1',
        },
        { tienda: 'Wong', estado: 'no_encontrado' },
      ],
    });
  });
});
```

- [ ] **Step 2: Ejecutar los tests y verificar que fallan**

Run: `npx vitest run test/scanHandler.test.ts test/scanIntegration.test.ts`
Expected: FAIL — `lib/scanHandler.ts` todavía no importa `buscarPlazaVea`/`buscarWong` ni devuelve `tiendas`, así que las aserciones sobre `result.tiendas` y las llamadas a los mocks fallan.

- [ ] **Step 3: Reemplazar el contenido completo de `lib/scanHandler.ts`**

```ts
import { ValidationError, IdentificationError } from './errors';
import { identifyProduct } from './identifyProduct';
import type { ProductIdentification } from './productIdentification';
import { buscarPlazaVea } from './stores/plazaVea';
import { buscarWong } from './stores/wong';
import type { StoreProduct } from './stores/types';

const DATA_URI_PATTERN = /^data:image\/(jpeg|png|gif|webp);base64,([A-Za-z0-9+/]+={0,2})$/;
const MAX_IMAGE_BASE64_LENGTH = 4_000_000; // ~3MB decoded; stays under Vercel's 4.5MB request body limit
const CONFIDENCE_THRESHOLD = 0.5;

export interface StoreResult {
  tienda: string;
  estado: 'encontrado' | 'no_encontrado' | 'error';
  producto?: string;
  precio?: number;
  url?: string;
  mensaje?: string;
}

export interface ScanResponseBody {
  identification: ProductIdentification;
  tiendas: StoreResult[];
}

async function searchStore(
  tienda: string,
  search: (identification: ProductIdentification) => Promise<StoreProduct | null>,
  identification: ProductIdentification
): Promise<StoreResult> {
  try {
    const match = await search(identification);
    if (match === null) {
      return { tienda, estado: 'no_encontrado' };
    }
    return { tienda, estado: 'encontrado', producto: match.producto, precio: match.precio, url: match.url };
  } catch (err) {
    return { tienda, estado: 'error', mensaje: err instanceof Error ? err.message : 'Error desconocido' };
  }
}

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

  if (identification.confianza < CONFIDENCE_THRESHOLD) {
    return { identification, tiendas: [] };
  }

  const tiendas = await Promise.all([
    searchStore('Plaza Vea', buscarPlazaVea, identification),
    searchStore('Wong', buscarWong, identification),
  ]);

  return { identification, tiendas };
}
```

- [ ] **Step 4: Ejecutar los tests y verificar que pasan**

Run: `npx vitest run test/scanHandler.test.ts test/scanIntegration.test.ts`
Expected: PASS (11 tests en scanHandler.test.ts, 2 tests en scanIntegration.test.ts)

- [ ] **Step 5: Ejecutar toda la suite**

Run: `npm test`
Expected: PASS — todos los tests de Plan 1 (sin cambios) más los nuevos de esta tarea y las Tasks 1-3, sin regresiones.

- [ ] **Step 6: Commit**

```bash
git add lib/scanHandler.ts test/scanHandler.test.ts test/scanIntegration.test.ts
git commit -m "feat: search Plaza Vea and Wong prices from handleScan"
```

---

### Task 5: Verificación manual en vivo contra Plaza Vea y Wong

Esta tarea no tiene test automatizado porque llama a las APIs reales de Plaza Vea y Wong por internet — los tests anteriores mockean `fetch` y `searchVtexStore`. Es la única forma de confirmar que la URL, el formato de respuesta VTEX y el matching funcionan de verdad hoy contra los sitios reales (que pueden cambiar sin aviso, como advierte el spec). No requiere cuenta de Vercel ni credenciales de IA — es independiente de la Task 7 del Plan 1.

**Files:**
- Create: `scripts/verify-stores.ts`
- Modify: `tsconfig.json` (agregar `scripts/**/*.ts` a `include`)

- [ ] **Step 1: Instalar `tsx` para ejecutar TypeScript directamente**

```bash
npm install -D tsx
```

- [ ] **Step 2: Agregar `scripts/**/*.ts` a `tsconfig.json`**

Edita el array `include` de `tsconfig.json` para que quede:

```json
  "include": ["api/**/*.ts", "lib/**/*.ts", "test/**/*.ts", "scripts/**/*.ts"]
```

- [ ] **Step 3: Crear `scripts/verify-stores.ts`**

```ts
import { buscarPlazaVea } from '../lib/stores/plazaVea';
import { buscarWong } from '../lib/stores/wong';
import type { ProductIdentification } from '../lib/productIdentification';

const identification: ProductIdentification = {
  marca: 'Gloria',
  nombre: 'Leche evaporada',
  presentacion: '400g',
  categoria: 'abarrotes',
  confianza: 0.9,
};

const [plazaVea, wong] = await Promise.all([
  buscarPlazaVea(identification).catch((err: unknown) => ({ error: String(err) })),
  buscarWong(identification).catch((err: unknown) => ({ error: String(err) })),
]);

console.log('Plaza Vea:', JSON.stringify(plazaVea, null, 2));
console.log('Wong:', JSON.stringify(wong, null, 2));
```

- [ ] **Step 4: Ejecutar el script contra las tiendas reales**

Run: `npx tsx scripts/verify-stores.ts`
Expected: ambas líneas imprimen un objeto con `producto`, `precio` (número) y `url` (un link real a `plazavea.com.pe` o `wong.pe`). Si alguna imprime `null`, prueba cambiar `identification.nombre` en el script a un término más genérico (ej. `"Leche"` en vez de `"Leche evaporada"`) y vuelve a correr — un `null` significa que ningún resultado de esa búsqueda superó el umbral de similitud, no necesariamente que la API falló.

- [ ] **Step 5: Documentar cualquier ajuste necesario**

Si la URL, el formato de respuesta, o los nombres de campos de VTEX no coinciden con lo esperado (por ejemplo, si alguna tienda cambió de plataforma), anota el error real y ajusta `lib/stores/vtexSearch.ts` en consecuencia. Vuelve a correr `npm test` después de cualquier ajuste para confirmar que los tests mockeados siguen pasando.

- [ ] **Step 6: Ejecutar la suite completa una última vez**

Run: `npm test`
Expected: PASS — todos los tests (Tasks 1-4 de este plan, más todo el Plan 1) siguen en verde.

- [ ] **Step 7: Commit**

```bash
git add scripts/verify-stores.ts tsconfig.json package.json package-lock.json
git commit -m "chore: add live verification script for store searches"
```

(Si el Step 5 requirió ajustes en `lib/stores/vtexSearch.ts`, inclúyelo en este commit o en uno adicional `fix: adjust vtexSearch after live verification`.)
