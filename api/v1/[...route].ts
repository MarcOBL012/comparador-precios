import type { VercelRequest, VercelResponse } from '@vercel/node';
import { getRouter } from '../../lib/app.js';
import { toErrorResult } from '../../lib/http/respond.js';

// Un solo punto de entrada para toda la API v1: el router interno reparte a cada controlador.
export const maxDuration = 60;

export default async function handler(req: VercelRequest, res: VercelResponse) {
  const url = new URL(req.url ?? '/', 'http://localhost');
  const path = url.pathname.replace(/^\/api\/v1/, '') || '/';
  // Vercel agrega el segmento comodín ("route") a la query: no es un parámetro de la persona.
  const { route: _route, ...query } = req.query;

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
