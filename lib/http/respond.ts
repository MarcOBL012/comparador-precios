import {
  ConfigError,
  ConflictError,
  NotFoundError,
  RateLimitedError,
  UnauthorizedError,
  UnprocessableError,
  UpstreamError,
  ValidationError,
  type FieldIssue,
} from '../errors.js';

export interface HttpResult {
  status: number;
  body?: unknown;
  headers?: Record<string, string>;
}

export const ok = (body: unknown): HttpResult => ({ status: 200, body });
export const created = (body: unknown, location: string): HttpResult => ({
  status: 201,
  body,
  headers: { Location: location },
});
export const noContent = (): HttpResult => ({ status: 204 });

interface ErrorBody {
  /** Texto para mostrar a la persona (el cliente móvil ya lee este campo). */
  error: string;
  /** Código estable para que el cliente decida qué hacer sin leer el texto. */
  codigo: string;
  detalles?: FieldIssue[];
}

function errorResult(status: number, body: ErrorBody, headers?: Record<string, string>): HttpResult {
  return { status, body, headers };
}

/**
 * Único lugar donde un error de dominio se vuelve código HTTP. Lo que no se reconoce es un 500
 * genérico: el detalle se registra en el servidor, nunca se devuelve al cliente.
 */
export function toErrorResult(err: unknown): HttpResult {
  if (err instanceof ValidationError) {
    return errorResult(400, { error: err.message, codigo: 'validacion', detalles: err.detalles });
  }
  if (err instanceof UnauthorizedError) {
    return errorResult(401, { error: err.message, codigo: 'no_autenticado' });
  }
  if (err instanceof NotFoundError) {
    return errorResult(404, { error: err.message, codigo: 'no_encontrado' });
  }
  if (err instanceof ConflictError) {
    return errorResult(409, { error: err.message, codigo: err.codigo });
  }
  if (err instanceof UnprocessableError) {
    return errorResult(422, { error: err.message, codigo: 'regla_de_negocio', detalles: err.detalles });
  }
  if (err instanceof RateLimitedError) {
    return errorResult(429, { error: err.message, codigo: 'demasiadas_consultas' }, {
      'Retry-After': String(err.retryAfterSeconds),
    });
  }
  if (err instanceof UpstreamError) {
    console.error('Servicio externo falló:', err.cause ?? err);
    return errorResult(502, { error: err.message, codigo: 'servicio_externo' });
  }
  if (err instanceof ConfigError) {
    console.error(err.message);
    return errorResult(503, { error: 'Servicio no disponible por configuración del servidor.', codigo: 'configuracion' });
  }
  console.error('Error no controlado:', err);
  return errorResult(500, { error: 'Error interno del servidor.', codigo: 'error_interno' });
}
