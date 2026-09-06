import { describe, it, expect, vi, beforeEach } from 'vitest';

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

describe('POST /api/scan (integración: scanHandler e identifyProduct reales)', () => {
  beforeEach(() => {
    generateTextMock.mockReset();
  });

  it('responde 400 cuando la imagen no es una data URI válida, sin llamar a la IA', async () => {
    const req = createMockReq('POST', { image: 'no-es-una-imagen' });
    const res = createMockRes();

    await handler(req, res);

    expect(res.statusCode).toBe(400);
    expect(generateTextMock).not.toHaveBeenCalled();
  });

  it('responde 200 con la identificación cuando la imagen es válida y la IA responde correctamente', async () => {
    const identification = {
      marca: 'Gloria',
      nombre: 'Leche evaporada',
      presentacion: '400g',
      categoria: 'abarrotes',
      confianza: 0.9,
    };
    generateTextMock.mockResolvedValue({ output: identification });

    const req = createMockReq('POST', { image: 'data:image/jpeg;base64,ABC123' });
    const res = createMockRes();

    await handler(req, res);

    expect(res.statusCode).toBe(200);
    expect(res.body).toEqual({ identification });
  });
});
