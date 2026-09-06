import type { VercelRequest, VercelResponse } from '@vercel/node';
import { handleScan } from '../lib/scanHandler.js';
import { ValidationError, IdentificationError } from '../lib/errors.js';

export const maxDuration = 60;

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== 'POST') {
    res.status(405).json({ error: 'Método no permitido. Usa POST.' });
    return;
  }

  try {
    const result = await handleScan(req.body);
    res.status(200).json(result);
  } catch (err) {
    console.error('POST /api/scan failed:', err);
    if (err instanceof IdentificationError && err.cause) {
      console.error('IdentificationError cause:', err.cause);
    }
    if (err instanceof ValidationError) {
      res.status(400).json({ error: err.message });
      return;
    }
    if (err instanceof IdentificationError) {
      res.status(502).json({ error: err.message });
      return;
    }
    res.status(500).json({ error: 'Error interno del servidor.' });
  }
}
