import type { VercelRequest, VercelResponse } from '@vercel/node';
import { getRouter } from '../../lib/app.js';
import { toErrorResult } from '../../lib/http/respond.js';

// Un solo punto de entrada para toda la API v1: el router interno reparte a cada controlador.
// vercel.json reescribe /api/v1/<ruta> a /api/v1/dispatch?path=<ruta>: el comodín [...x] de Vercel solo
// cubría un segmento, así que rutas como /watches/{id}/history o /internal/check-due daban 404.
export const maxDuration = 60;

export default async function handler(req: VercelRequest, res: VercelResponse) {
  const { path: rawPath, ...query } = req.query;
  const joined = Array.isArray(rawPath) ? rawPath.join('/') : rawPath ?? '';
  const path = '/' + joined.replace(/^\/+/, '');

  let result;
  try {
    result = await getRouter().dispatch({
      method: req.method ?? 'GET',
      path,
      query,
      headers: req.headers,
      body: req.body,
    });
  } catch (err) {
    result = toErrorResult(err);
  }

  for (const [name, value] of Object.entries(result.headers ?? {})) {
    res.setHeader(name, value);
  }
  res.status(result.status);
  if (result.body === undefined) {
    res.end();
  } else {
    res.json(result.body);
  }
}
