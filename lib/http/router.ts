import { isCronRequest, type AuthUser } from '../auth.js';
import { NotFoundError, UnauthorizedError, ValidationError } from '../errors.js';
import { toErrorResult, type HttpResult } from './respond.js';

export type Method = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';

export interface RequestInfo {
  method: string;
  /** Ruta sin el prefijo /api/v1 ni la query, ej. "/watches/123/check". */
  path: string;
  query: Record<string, string | string[] | undefined>;
  headers: Record<string, string | string[] | undefined>;
  body: unknown;
}

export interface Context {
  params: Record<string, string>;
  query: Record<string, string | string[] | undefined>;
  body: unknown;
  /** Presente en rutas con auth "user". */
  user: AuthUser;
}

export type Handler = (ctx: Context) => Promise<HttpResult>;

/** "user" = sesión de Clerk; "cron" = secreto compartido con el cron; "none" = pública. */
export type AuthMode = 'user' | 'cron' | 'none';

interface Route {
  method: Method;
  segments: string[];
  auth: AuthMode;
  handler: Handler;
}

export interface RouterDeps {
  authenticate: (req: { headers: RequestInfo['headers'] }) => Promise<AuthUser | null>;
}

const splitPath = (path: string) => path.split('/').filter(Boolean);

export class Router {
  private readonly routes: Route[] = [];

  constructor(private readonly deps: RouterDeps) {}

  add(method: Method, pattern: string, auth: AuthMode, handler: Handler): this {
    this.routes.push({ method, segments: splitPath(pattern), auth, handler });
    return this;
  }

  private match(segments: string[], route: Route): Record<string, string> | null {
    if (route.segments.length !== segments.length) {
      return null;
    }
    const params: Record<string, string> = {};
    for (let i = 0; i < segments.length; i++) {
      const expected = route.segments[i];
      if (expected.startsWith(':')) {
        params[expected.slice(1)] = decodeURIComponent(segments[i]);
      } else if (expected !== segments[i]) {
        return null;
      }
    }
    return params;
  }

  async dispatch(req: RequestInfo): Promise<HttpResult> {
    try {
      const segments = splitPath(req.path);
      const samePath = this.routes
        .map((route) => ({ route, params: this.match(segments, route) }))
        .filter((m): m is { route: Route; params: Record<string, string> } => m.params !== null);

      if (samePath.length === 0) {
        throw new NotFoundError('Ruta no encontrada.');
      }
      const hit = samePath.find((m) => m.route.method === req.method.toUpperCase());
      if (!hit) {
        return {
          status: 405,
          headers: { Allow: [...new Set(samePath.map((m) => m.route.method))].join(', ') },
          body: { error: 'Método no permitido para esta ruta.', codigo: 'metodo_no_permitido' },
        };
      }

      const { route, params } = hit;
      let user: AuthUser = { clerkId: '' };
      if (route.auth === 'user') {
        const authenticated = await this.deps.authenticate({ headers: req.headers });
        if (!authenticated) {
          throw new UnauthorizedError();
        }
        user = authenticated;
      } else if (route.auth === 'cron' && !isCronRequest({ headers: req.headers })) {
        throw new UnauthorizedError('Secreto de cron inválido.');
      }

      return await route.handler({ params, query: req.query, body: req.body, user });
    } catch (err) {
      return toErrorResult(err);
    }
  }
}

/** Vercel entrega el cuerpo ya parseado; si el JSON venía roto llega como string o sin parsear. */
export function requireJsonBody(body: unknown): Record<string, unknown> {
  if (typeof body !== 'object' || body === null || Array.isArray(body)) {
    throw new ValidationError('El cuerpo debe ser un objeto JSON.');
  }
  return body as Record<string, unknown>;
}
