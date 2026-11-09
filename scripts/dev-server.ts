// Servidor local de la API v1 (sin Vercel): atiende http://localhost:3000/api/v1/... con el mismo router
// que usa api/v1/dispatch.ts. Sirve para probar con Postman y para tomar evidencia sin cuenta de Vercel.
//
//   DATABASE_URL=postgresql://postgres:postgres@localhost:54329/comparador \
//   AUTH_DEV_MODE=1 CRON_SECRET=cambia-esto npm run dev:api
import { createServer } from 'node:http';
import { getRouter } from '../lib/app.js';
import { toErrorResult } from '../lib/http/respond.js';

const PORT = Number(process.env.PORT ?? 3000);
const PREFIX = '/api/v1';

async function readBody(req: import('node:http').IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  for await (const chunk of req) {
    chunks.push(chunk as Buffer);
  }
  const raw = Buffer.concat(chunks).toString('utf8');
  if (raw === '') {
    return undefined;
  }
  // Igual que Vercel: JSON válido llega parseado; si viene roto llega como texto y el controller lo rechaza.
  try {
    return JSON.parse(raw);
  } catch {
    return raw;
  }
}

createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', `http://${req.headers.host ?? 'localhost'}`);
  let result;
  try {
    if (!url.pathname.startsWith(PREFIX)) {
      res.writeHead(404, { 'Content-Type': 'application/json' }).end(JSON.stringify({ error: 'Usa /api/v1/...', codigo: 'no_encontrado' }));
      return;
    }
    const query: Record<string, string | string[]> = {};
    for (const key of new Set(url.searchParams.keys())) {
      const values = url.searchParams.getAll(key);
      query[key] = values.length > 1 ? values : values[0];
    }
    result = await getRouter().dispatch({
      method: req.method ?? 'GET',
      path: url.pathname.slice(PREFIX.length) || '/',
      query,
      headers: req.headers,
      body: await readBody(req),
    });
  } catch (err) {
    result = toErrorResult(err);
  }

  const headers: Record<string, string> = { ...(result.headers ?? {}) };
  if (result.body !== undefined) {
    headers['Content-Type'] = 'application/json; charset=utf-8';
  }
  res.writeHead(result.status, headers);
  res.end(result.body === undefined ? undefined : JSON.stringify(result.body));
}).listen(PORT, () => {
  console.log(`API v1 local en http://localhost:${PORT}${PREFIX}`);
});
