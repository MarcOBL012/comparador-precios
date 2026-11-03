export interface FieldIssue {
  campo: string;
  mensaje: string;
}

/** 400: la petición está mal formada (JSON roto, tipos incorrectos, parámetro ilegible). */
export class ValidationError extends Error {
  constructor(
    message: string,
    public readonly detalles?: FieldIssue[]
  ) {
    super(message);
    this.name = 'ValidationError';
  }
}

export class IdentificationError extends Error {
  public readonly cause?: unknown;

  constructor(message: string, cause?: unknown) {
    super(message);
    this.name = 'IdentificationError';
    this.cause = cause;
  }
}

/** 401: falta la sesión o es inválida. */
export class UnauthorizedError extends Error {
  constructor(message = 'No autenticado. Inicia sesión para continuar.') {
    super(message);
    this.name = 'UnauthorizedError';
  }
}

/** 404: no existe, o existe pero es de otra persona (no se revela cuál de los dos). */
export class NotFoundError extends Error {
  constructor(message = 'Recurso no encontrado.') {
    super(message);
    this.name = 'NotFoundError';
  }
}

/** 409: la petición es válida pero choca con el estado actual (duplicado, transición, límite). */
export class ConflictError extends Error {
  constructor(
    message: string,
    public readonly codigo: string = 'conflicto'
  ) {
    super(message);
    this.name = 'ConflictError';
  }
}

/** 422: la petición está bien formada pero rompe una regla de negocio. */
export class UnprocessableError extends Error {
  constructor(
    message: string,
    public readonly detalles?: FieldIssue[]
  ) {
    super(message);
    this.name = 'UnprocessableError';
  }
}

/** 429: demasiadas consultas; `retryAfterSeconds` sale en el encabezado Retry-After. */
export class RateLimitedError extends Error {
  constructor(
    message: string,
    public readonly retryAfterSeconds: number
  ) {
    super(message);
    this.name = 'RateLimitedError';
  }
}

/** 502: falló un servicio del que dependemos (tiendas, IA, notificaciones). */
export class UpstreamError extends Error {
  constructor(
    message: string,
    public readonly cause?: unknown
  ) {
    super(message);
    this.name = 'UpstreamError';
  }
}

/** 503: falta configuración en el servidor (por ejemplo DATABASE_URL). */
export class ConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ConfigError';
  }
}
