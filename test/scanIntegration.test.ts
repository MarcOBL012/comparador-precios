import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const { generateTextMock, objectMock } = vi.hoisted(() => ({
  generateTextMock: vi.fn(),
  objectMock: vi.fn((config: unknown) => ({ __schemaConfig: config })),
}));

vi.mock('ai', () => ({
  generateText: generateTextMock,
  Output: { object: objectMock },
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

function vtexResponse(body: unknown, ok = true, status = 200) {
  return { ok, status, json: async () => body } as Response;
}

describe('POST /api/scan (integración completa: scanHandler, identifyProduct y búsqueda VTEX reales)', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    generateTextMock.mockReset();
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('responde 400 cuando la imagen no es una data URI válida, sin llamar a la IA ni a las tiendas', async () => {
    const req = createMockReq('POST', { image: 'no-es-una-imagen' });
    const res = createMockRes();

    await handler(req, res);

    expect(res.statusCode).toBe(400);
    expect(generateTextMock).not.toHaveBeenCalled();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('responde 200 con la identificación y las tiendas recorriendo la cadena real hasta la búsqueda VTEX', async () => {
    const identification = {
      marca: 'Gloria',
      nombre: 'Leche evaporada',
      presentacion: '400g',
      categoria: 'abarrotes',
      confianza: 0.9,
    };
    generateTextMock.mockResolvedValue({ output: identification });
    fetchMock.mockResolvedValue(
      vtexResponse([
        {
          productName: 'Leche Evaporada Gloria 400g',
          brand: 'Gloria',
          link: 'https://tienda.example.pe/p/1',
          items: [{ sellers: [{ commertialOffer: { Price: 4.5, IsAvailable: true } }] }],
        },
      ])
    );

    const req = createMockReq('POST', { image: 'data:image/jpeg;base64,ABC123' });
    const res = createMockRes();

    await handler(req, res);

    expect(res.statusCode).toBe(200);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(res.body).toEqual({
      identification,
      tiendas: [
        {
          tienda: 'Plaza Vea',
          estado: 'encontrado',
          producto: 'Leche Evaporada Gloria 400g',
          precio: 4.5,
          url: 'https://tienda.example.pe/p/1',
        },
        {
          tienda: 'Wong',
          estado: 'encontrado',
          producto: 'Leche Evaporada Gloria 400g',
          precio: 4.5,
          url: 'https://tienda.example.pe/p/1',
        },
      ],
    });
  });
});
