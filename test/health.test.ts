import { describe, it, expect } from 'vitest';
import { getHealthStatus } from '../lib/health';

describe('getHealthStatus', () => {
  it('devuelve status ok', () => {
    expect(getHealthStatus()).toEqual({ status: 'ok' });
  });
});
