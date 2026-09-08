import type { VercelRequest } from '@vercel/node';
import { verifyToken } from '@clerk/backend';

export async function isAuthenticated(req: VercelRequest): Promise<boolean> {
  const header = req.headers['authorization'];
  const value = Array.isArray(header) ? header[0] : header;
  const match = value?.match(/^Bearer\s+(.+)$/i);
  const token = match?.[1];
  if (!token) {
    return false;
  }
  try {
    // La wrapper de "legacy return" de verifyToken resuelve con un JwtPayload plano en
    // éxito y lanza TokenVerificationError en falla — nunca resuelve con un campo
    // `.errors`. JwtPayload tiene index signature ([k: string]: unknown), así que
    // `result.errors` tipaba como `unknown` sin que tsc lo detectara, aunque ese campo
    // nunca puede estar presente en la práctica.
    await verifyToken(token, { secretKey: process.env.CLERK_SECRET_KEY });
    return true;
  } catch {
    return false;
  }
}
