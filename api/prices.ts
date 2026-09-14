import type { VercelRequest, VercelResponse } from '@vercel/node';
import { handlePrices } from '../lib/scanHandler.js';
import { ValidationError } from '../lib/errors.js';
import { isAuthenticated } from '../lib/auth.js';

export const maxDuration = 30;

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== 'POST') {
    res.status(405).json({ error: 'Método no permitido. Usa POST.' });
    return;
  }

  if (!(await isAuthenticated(req))) {
    res.status(401).json({ error: 'No autenticado. Inicia sesión para consultar precios.' });
    return;
  }

  try {
    res.status(200).json(await handlePrices(req.body));
  } catch (err) {
    if (err instanceof ValidationError) {
      res.status(400).json({ error: err.message });
      return;
    }
    console.error('POST /api/prices failed:', err);
    res.status(500).json({ error: 'Error interno del servidor.' });
  }
}
