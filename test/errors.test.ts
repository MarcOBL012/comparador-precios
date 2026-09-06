import { describe, it, expect } from 'vitest';
import { ValidationError, IdentificationError } from '../lib/errors';

describe('errores tipados', () => {
  it('ValidationError tiene name y message correctos', () => {
    const err = new ValidationError('campo inválido');
    expect(err.name).toBe('ValidationError');
    expect(err.message).toBe('campo inválido');
    expect(err).toBeInstanceOf(Error);
  });

  it('IdentificationError tiene name, message y cause correctos', () => {
    const cause = new Error('fallo de red');
    const err = new IdentificationError('no se pudo identificar', cause);
    expect(err.name).toBe('IdentificationError');
    expect(err.message).toBe('no se pudo identificar');
    expect(err.cause).toBe(cause);
    expect(err).toBeInstanceOf(Error);
  });
});
