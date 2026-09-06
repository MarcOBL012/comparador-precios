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
