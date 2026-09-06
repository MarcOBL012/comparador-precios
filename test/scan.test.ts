import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { VercelRequest, VercelResponse } from '@vercel/node';

const { handleScanMock } = vi.hoisted(() => ({
  handleScanMock: vi.fn(),
}));

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
    expect(res.body).toEqual({ error: 'Método no permitido. Usa POST.' });
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
