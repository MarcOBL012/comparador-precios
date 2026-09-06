import type { VercelRequest } from '@vercel/node';
import { verifyToken } from '@clerk/backend';

export async function isAuthenticated(req: VercelRequest): Promise<boolean> {
  const header = req.headers['authorization'];
  const value = Array.isArray(header) ? header[0] : header;
  const token = value?.replace(/^Bearer\s+/i, '');
  if (!token) {
    return false;
  }
  try {
    const result = await verifyToken(token, { secretKey: process.env.CLERK_SECRET_KEY });
    return !result.errors;
  } catch {
    return false;
  }
}
