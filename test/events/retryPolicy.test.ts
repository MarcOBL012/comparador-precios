import { describe, expect, it } from 'vitest';
import { ValidationError } from '../../lib/errors';
import { EventContractError } from '../../lib/events/catalog';
import {
  BASE_BACKOFF_SECONDS,
  MAX_ATTEMPTS,
  PermanentError,
  backoffSeconds,
  classify,
  errorMessage,
  outcomeAfterFailure,
} from '../../lib/events/retryPolicy';

const sinVariacion = () => 0.5; // factor de variación = 1

describe('política de reintentos', () => {
  it('la espera se duplica en cada intento: 30 s, 1, 2, 4, 8 y 16 min', () => {
    expect([1, 2, 3, 4, 5, 6].map((n) => backoffSeconds(n, sinVariacion))).toEqual([30, 60, 120, 240, 480, 960]);
    expect(BASE_BACKOFF_SECONDS).toBe(30);
  });

  it('la variación aleatoria no pasa de ±20 %', () => {
    expect(backoffSeconds(3, () => 0)).toBe(96); // 120 × 0.8
    expect(backoffSeconds(3, () => 1)).toBe(144); // 120 × 1.2
  });

  it('distingue errores permanentes de transitorios', () => {
    expect(classify(new PermanentError('x'))).toBe('permanent');
    expect(classify(new ValidationError('x'))).toBe('permanent');
    expect(classify(new EventContractError('x'))).toBe('permanent');
    expect(classify(new Error('timeout'))).toBe('transient');
    expect(classify('texto suelto')).toBe('transient');
  });

  it('un error transitorio se reintenta con la espera del intento', () => {
    expect(outcomeAfterFailure(1, new Error('503'), sinVariacion)).toEqual({ status: 'failed', retryInSeconds: 30 });
    expect(outcomeAfterFailure(5, new Error('503'), sinVariacion)).toEqual({ status: 'failed', retryInSeconds: 480 });
  });

  it('un error permanente va directo a la cola de fallos, sin gastar intentos', () => {
    expect(outcomeAfterFailure(1, new PermanentError('datos inválidos'))).toEqual({ status: 'dead' });
  });

  it('al agotar los intentos pasa a la cola de fallos', () => {
    expect(MAX_ATTEMPTS).toBe(6);
    expect(outcomeAfterFailure(MAX_ATTEMPTS, new Error('503'))).toEqual({ status: 'dead' });
  });

  it('acorta el mensaje de error para guardarlo', () => {
    expect(errorMessage(new Error('x'.repeat(2000))).length).toBe(500);
    expect(errorMessage(new TypeError('boom'))).toBe('TypeError: boom');
  });
});
