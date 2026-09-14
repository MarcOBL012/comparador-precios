import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { VercelRequest, VercelResponse } from '@vercel/node';

const { handlePricesMock, isAuthenticatedMock } = vi.hoisted(() => ({
  handlePricesMock: vi.fn(),
  isAuthenticatedMock: vi.fn(),
}));

vi.mock('../lib/scanHandler', () => ({ handlePrices: handlePricesMock }));
vi.mock('../lib/auth', () => ({ isAuthenticated: isAuthenticatedMock }));

import handler from '../api/prices';
import { ValidationError } from '../lib/errors';

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

const req = (method: string, body: unknown = {}) => ({ method, body }) as VercelRequest;

describe('POST /api/prices', () => {
  beforeEach(() => {
    handlePricesMock.mockReset();
    isAuthenticatedMock.mockReset();
    isAuthenticatedMock.mockResolvedValue(true);
  });

  it('responde 405 si el método no es POST', async () => {
    const res = createMockRes();
    await handler(req('GET'), res);
    expect(res.statusCode).toBe(405);
  });

  it('responde 401 sin sesión y no consulta tiendas', async () => {
    isAuthenticatedMock.mockResolvedValue(false);
    const res = createMockRes();

    await handler(req('POST'), res);

    expect(res.statusCode).toBe(401);
    expect(handlePricesMock).not.toHaveBeenCalled();
  });

  it('responde 200 con los precios', async () => {
    const body = { tiendas: [], busquedaWeb: 'no_aplica' };
    handlePricesMock.mockResolvedValue(body);
    const res = createMockRes();

    await handler(req('POST', { identification: {} }), res);

    expect(res.statusCode).toBe(200);
    expect(res.body).toEqual(body);
  });

  it('responde 400 ante una identificación inválida', async () => {
    handlePricesMock.mockRejectedValue(new ValidationError('falta identification'));
    const res = createMockRes();

    await handler(req('POST'), res);

    expect(res.statusCode).toBe(400);
    expect(res.body).toEqual({ error: 'falta identification' });
  });

  it('responde 500 ante cualquier otro error', async () => {
    handlePricesMock.mockRejectedValue(new Error('boom'));
    vi.spyOn(console, 'error').mockImplementation(() => {});
    const res = createMockRes();

    await handler(req('POST'), res);

    expect(res.statusCode).toBe(500);
  });
});
