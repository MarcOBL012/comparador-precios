import { describe, expect, it } from 'vitest';
import { EVENT_TYPES, EventContractError, isEventType, validatePayload } from '../../lib/events/catalog';
import { InMemoryEventBus } from '../../lib/events/memory';
import { ConsumerRegistry } from '../../lib/events/subscriptions';
import type { Consumer } from '../../lib/events/types';

const WATCH_ID = '6f1c5f0e-0c3a-4b7e-9d1e-2f6a7c9b1a10';
const consumer = (name: string, handles: Consumer['handles']): Consumer => ({ name, handles, handle: async () => {} });

describe('catálogo de eventos', () => {
  it('acepta una carga útil que cumple el contrato', () => {
    expect(validatePayload('watch.checked', { ofertas: 3, descartadas: 0 })).toEqual({ ofertas: 3, descartadas: 0 });
    expect(validatePayload('alert.created', { alertId: 5, watchId: WATCH_ID })).toEqual({ alertId: 5, watchId: WATCH_ID });
  });

  it('rechaza campos de más, de menos o con el tipo equivocado (es un bug del productor)', () => {
    expect(() => validatePayload('watch.checked', { ofertas: 3, descartadas: 0, extra: true })).toThrow(EventContractError);
    expect(() => validatePayload('watch.checked', { ofertas: 3 })).toThrow(/descartadas/);
    expect(() => validatePayload('watch.checked', { ofertas: -1, descartadas: 0 })).toThrow(EventContractError);
    expect(() => validatePayload('alert.created', { alertId: 5, watchId: 'no-es-uuid' })).toThrow(/watchId/);
  });

  it('rechaza un tipo desconocido', () => {
    expect(() => validatePayload('pedido.creado' as never, {})).toThrow(/desconocido/);
    expect(isEventType('watch.checked')).toBe(true);
    expect(isEventType('toString')).toBe(false);
    expect(EVENT_TYPES).toEqual(expect.arrayContaining(['watch.checked', 'alert.created']));
  });
});

describe('bus en memoria', () => {
  it('valida la carga útil al publicar', async () => {
    const bus = new InMemoryEventBus();
    await expect(bus.publish(undefined, { type: 'watch.checked', aggregateType: 'watch', aggregateId: 'w', payload: { ofertas: 'x' } as never })).rejects.toThrow(EventContractError);
    expect(bus.published).toHaveLength(0);
  });

  it('no publica dos veces el mismo dedupeKey', async () => {
    const bus = new InMemoryEventBus();
    const input = { type: 'watch.checked' as const, aggregateType: 'watch', aggregateId: 'w', payload: { ofertas: 1, descartadas: 0 }, dedupeKey: 'w-2026-11-24T11' };
    expect(await bus.publish(undefined, input)).toBe(1);
    expect(await bus.publish(undefined, input)).toBeNull();
    expect(bus.published).toHaveLength(1);
  });
});

describe('registro de consumidores', () => {
  it('dice quién escucha cada tipo', () => {
    const registry = new ConsumerRegistry([consumer('metrics', ['watch.checked', 'alert.created']), consumer('notifier', ['alert.created'])]);
    expect(registry.consumersFor('alert.created')).toEqual(['metrics', 'notifier']);
    expect(registry.consumersFor('watch.checked')).toEqual(['metrics']);
  });

  it('rechaza nombres duplicados o inválidos', () => {
    expect(() => new ConsumerRegistry([consumer('a', []), consumer('a', [])])).toThrow(/duplicado/);
    expect(() => new ConsumerRegistry([consumer('Mal Nombre', [])])).toThrow(/inválido/);
  });
});
