import { afterEach, describe, expect, it, vi } from 'vitest';

const { verifyTokenMock } = vi.hoisted(() => ({ verifyTokenMock: vi.fn() }));
vi.mock('@clerk/backend', () => ({ verifyToken: verifyTokenMock }));

import { authenticate, isCronRequest } from '../lib/auth';

describe('authenticate', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    verifyTokenMock.mockReset();
  });

  it('devuelve el sub del token de Clerk', async () => {
    verifyTokenMock.mockResolvedValue({ sub: 'user_123' });

    expect(await authenticate({ headers: { authorization: 'Bearer abc' } })).toEqual({ clerkId: 'user_123' });
  });

  it('devuelve null si el token es inválido o no trae sub', async () => {
    verifyTokenMock.mockRejectedValueOnce(new Error('expirado'));
    expect(await authenticate({ headers: { authorization: 'Bearer abc' } })).toBeNull();

    verifyTokenMock.mockResolvedValueOnce({});
    expect(await authenticate({ headers: { authorization: 'Bearer abc' } })).toBeNull();
  });

  it('el modo desarrollo solo funciona con AUTH_DEV_MODE=1 y nunca en Vercel', async () => {
    const req = { headers: { 'x-dev-user': 'ana' } };
    expect(await authenticate(req)).toBeNull();

    vi.stubEnv('AUTH_DEV_MODE', '1');
    expect(await authenticate(req)).toEqual({ clerkId: 'dev_ana' });

    vi.stubEnv('VERCEL', '1');
    expect(await authenticate(req)).toBeNull();
  });
});

describe('isCronRequest', () => {
  afterEach(() => vi.unstubAllEnvs());

  it('acepta solo el secreto exacto; sin CRON_SECRET configurado rechaza todo', () => {
    vi.stubEnv('CRON_SECRET', 'secreto-largo');
    expect(isCronRequest({ headers: { authorization: 'Bearer secreto-largo' } })).toBe(true);
    expect(isCronRequest({ headers: { authorization: 'Bearer otro-secreto' } })).toBe(false);
    expect(isCronRequest({ headers: {} })).toBe(false);

    vi.stubEnv('CRON_SECRET', '');
    expect(isCronRequest({ headers: { authorization: 'Bearer ' } })).toBe(false);
  });
});
