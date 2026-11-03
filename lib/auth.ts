import { verifyToken } from '@clerk/backend';
import { timingSafeEqual } from 'node:crypto';

type Headers = Record<string, string | string[] | undefined>;

export interface AuthUser {
  /** `sub` del token de Clerk: identifica a la persona; nunca se lee del cuerpo de la petición. */
  clerkId: string;
}

function firstHeader(headers: Headers, name: string): string | undefined {
  const value = headers[name];
  return Array.isArray(value) ? value[0] : value;
}

function bearerToken(headers: Headers): string | undefined {
  return firstHeader(headers, 'authorization')?.match(/^Bearer\s+(.+)$/i)?.[1];
}

/**
 * Modo desarrollo para correr y probar la API sin una cuenta de Clerk (Postman, evaluadores):
 * `AUTH_DEV_MODE=1` + encabezado `X-Dev-User`. Jamás se activa en Vercel, aunque la variable llegue.
 */
function devUser(headers: Headers): AuthUser | null {
  if (process.env.AUTH_DEV_MODE !== '1' || process.env.VERCEL) {
    return null;
  }
  const id = firstHeader(headers, 'x-dev-user')?.trim();
  return id ? { clerkId: `dev_${id}` } : null;
}

export async function authenticate(req: { headers: Headers }): Promise<AuthUser | null> {
  const dev = devUser(req.headers);
  if (dev) {
    return dev;
  }
  const token = bearerToken(req.headers);
  if (!token) {
    return null;
  }
  try {
    const payload = await verifyToken(token, { secretKey: process.env.CLERK_SECRET_KEY });
    return typeof payload.sub === 'string' && payload.sub ? { clerkId: payload.sub } : null;
  } catch {
    return null;
  }
}

/** Lo usan los endpoints que solo necesitan saber si hay sesión, sin identificar a la persona. */
export async function isAuthenticated(req: { headers: Headers }): Promise<boolean> {
  const token = bearerToken(req.headers);
  if (!token) {
    return false;
  }
  try {
    // La wrapper de "legacy return" de verifyToken resuelve con un JwtPayload plano en
    // éxito y lanza TokenVerificationError en falla — nunca resuelve con un campo `.errors`.
    await verifyToken(token, { secretKey: process.env.CLERK_SECRET_KEY });
    return true;
  } catch {
    return false;
  }
}

/** Vercel Cron manda `Authorization: Bearer $CRON_SECRET`; se compara en tiempo constante. */
export function isCronRequest(req: { headers: Headers }): boolean {
  const secret = process.env.CRON_SECRET;
  const token = bearerToken(req.headers);
  if (!secret || !token) {
    return false;
  }
  const a = Buffer.from(token);
  const b = Buffer.from(secret);
  return a.length === b.length && timingSafeEqual(a, b);
}
