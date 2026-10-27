import type { VercelRequest, VercelResponse } from '@vercel/node';
import { handleRoute } from '../lib/extrasHandler.js';
import { ValidationError } from '../lib/errors.js';
import { isAuthenticated } from '../lib/auth.js';

export const maxDuration = 20;

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== 'POST') {
    res.status(405).json({ error: 'Método no permitido. Usa POST.' });
    return;
  }

  if (!(await isAuthenticated(req))) {
    res.status(401).json({ error: 'No autenticado. Inicia sesión para ver la ruta.' });
    return;
  }

  try {
    res.status(200).json(await handleRoute(req.body));
  } catch (err) {
    if (err instanceof ValidationError) {
      res.status(400).json({ error: err.message });
      return;
    }
    console.error('POST /api/route failed:', err);
    res.status(502).json({ error: 'No se pudo calcular la ruta.' });
  }
}
