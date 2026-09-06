import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { VercelRequest } from '@vercel/node';

const { verifyTokenMock } = vi.hoisted(() => ({
  verifyTokenMock: vi.fn(),
}));

vi.mock('@clerk/backend', () => ({
  verifyToken: verifyTokenMock,
}));

import { isAuthenticated } from '../lib/auth';

function createMockReq(authorization?: string): VercelRequest {
  return { headers: authorization ? { authorization } : {} } as VercelRequest;
}

describe('isAuthenticated', () => {
  beforeEach(() => {
    verifyTokenMock.mockReset();
  });

  it('devuelve false si no hay header Authorization', async () => {
    const result = await isAuthenticated(createMockReq());

    expect(result).toBe(false);
    expect(verifyTokenMock).not.toHaveBeenCalled();
  });

  it('devuelve false si el header no tiene el prefijo Bearer', async () => {
    const result = await isAuthenticated(createMockReq('token-sin-prefijo'));

    expect(result).toBe(false);
    expect(verifyTokenMock).not.toHaveBeenCalled();
  });

  it('devuelve true si el token es válido', async () => {
    verifyTokenMock.mockResolvedValue({ data: { sub: 'user_123' }, errors: null });

    const result = await isAuthenticated(createMockReq('Bearer token-valido'));

    expect(result).toBe(true);
    expect(verifyTokenMock).toHaveBeenCalledWith('token-valido', { secretKey: process.env.CLERK_SECRET_KEY });
  });

  it('usa el primer valor si el header llega como array', async () => {
    verifyTokenMock.mockResolvedValue({ data: { sub: 'user_123' }, errors: null });
    const req = { headers: { authorization: ['Bearer token-valido', 'Bearer otro'] } } as unknown as import('@vercel/node').VercelRequest;

    const result = await isAuthenticated(req);

    expect(result).toBe(true);
    expect(verifyTokenMock).toHaveBeenCalledWith('token-valido', { secretKey: process.env.CLERK_SECRET_KEY });
  });

  it('devuelve false si verifyToken resuelve con errores', async () => {
    verifyTokenMock.mockResolvedValue({ data: null, errors: [{ message: 'inválido' }] });

    const result = await isAuthenticated(createMockReq('Bearer token-invalido'));

    expect(result).toBe(false);
  });

  it('devuelve false si verifyToken lanza una excepción', async () => {
    verifyTokenMock.mockRejectedValue(new Error('token malformado'));

    const result = await isAuthenticated(createMockReq('Bearer token-malformado'));

    expect(result).toBe(false);
  });
});
