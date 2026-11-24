import { ValidationError } from '../errors.js';
import { EventContractError } from './catalog.js';

/** Intentos totales antes de mandar la entrega a la cola de fallos (dead). */
export const MAX_ATTEMPTS = 6;
/** Espera tras el primer fallo; se duplica en cada intento: 30 s, 1 min, 2, 4, 8 y 16 min. */
export const BASE_BACKOFF_SECONDS = 30;
/** Variación aleatoria (±20 %) para que muchos fallos simultáneos no reintenten todos a la vez. */
const JITTER = 0.2;

/** Un error que no se arregla reintentando (datos inválidos, bug del contrato): va directo a «dead». */
export class PermanentError extends Error {
  constructor(message: string, options?: { cause?: unknown }) {
    super(message, options);
    this.name = 'PermanentError';
  }
}

export type ErrorKind = 'permanent' | 'transient';

export function classify(err: unknown): ErrorKind {
  if (err instanceof PermanentError || err instanceof ValidationError || err instanceof EventContractError) {
    return 'permanent';
  }
  return 'transient';
}

/** `attempt` = número de intentos ya realizados (1 tras el primer fallo). */
export function backoffSeconds(attempt: number, random: () => number = Math.random): number {
  const base = BASE_BACKOFF_SECONDS * 2 ** Math.max(0, attempt - 1);
  const factor = 1 + (random() * 2 - 1) * JITTER;
  return Math.round(base * factor);
}

export type Outcome = { status: 'failed'; retryInSeconds: number } | { status: 'dead' };

/** Qué hacer con una entrega que acaba de fallar en su intento número `attempt`. */
export function outcomeAfterFailure(attempt: number, err: unknown, random: () => number = Math.random): Outcome {
  if (classify(err) === 'permanent' || attempt >= MAX_ATTEMPTS) {
    return { status: 'dead' };
  }
  return { status: 'failed', retryInSeconds: backoffSeconds(attempt, random) };
}

export function errorMessage(err: unknown): string {
  const text = err instanceof Error ? `${err.name}: ${err.message}` : String(err);
  return text.slice(0, 500);
}
