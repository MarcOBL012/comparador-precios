import { describe, expect, it, vi } from 'vitest';
import { PgEventBus } from '../../lib/events/bus';

/** Una «transacción» falsa: registra si se la usó. Sirve para probar qué escribe (y qué no) el bus sin una base. */
const fakeTx = () => {
  const calls: unknown[][] = [];
  const tx = Object.assign(
    vi.fn(async (...args: unknown[]) => {
      calls.push(args);
      return [{ id: '7' }];
    }),
    { json: (value: unknown) => value }
  );
  return { tx: tx as never, calls };
};

const watchCreated = {
  type: 'watch.created' as const,
  aggregateType: 'watch',
  aggregateId: 'w1',
  payload: { watchId: '6f1c5f0e-0c3a-4b7e-9d1e-2f6a7c9b1a10', productId: '5002f1a4-3274-40f8-8df2-1f5d53574406', precioObjetivo: 2.5 },
};
const watchChecked = {
  type: 'watch.checked' as const,
  aggregateType: 'watch',
  aggregateId: 'w1',
  payload: { ofertas: 1, descartadas: 0 },
};

describe('PgEventBus con el despacho apagado (como producción hoy)', () => {
  const bus = new PgEventBus(() => ['metrics'], () => false);

  it('no escribe los tipos nuevos: nadie los leería y la bandeja solo crecería', async () => {
    const { tx, calls } = fakeTx();
    expect(await bus.publish(tx, watchCreated)).toBeNull();
    expect(calls).toHaveLength(0);
  });

  it('sigue escribiendo los dos tipos que ya existían, con las columnas originales', async () => {
    const { tx, calls } = fakeTx();
    expect(await bus.publish(tx, watchChecked)).toBe(7);
    expect(calls).toHaveLength(1); // una sola sentencia: sin entregas por consumidor
  });

  it('igual valida la carga útil, también apagado', async () => {
    const { tx } = fakeTx();
    await expect(bus.publish(tx, { ...watchChecked, payload: { ofertas: 'x' } as never })).rejects.toThrow(/inválida/);
  });
});

describe('PgEventBus con el despacho activo', () => {
  it('escribe el evento y una entrega por consumidor suscrito', async () => {
    const { tx, calls } = fakeTx();
    const bus = new PgEventBus(() => ['metrics', 'alert_evaluator'], () => true);
    expect(await bus.publish(tx, watchChecked)).toBe(7);
    expect(calls).toHaveLength(3); // el evento + 2 entregas
  });

  it('no crea entregas si el dedupeKey ya existía', async () => {
    const calls: unknown[][] = [];
    const tx = Object.assign(
      vi.fn(async (...args: unknown[]) => {
        calls.push(args);
        return []; // «on conflict do nothing» no devuelve fila
      }),
      { json: (v: unknown) => v }
    );
    const bus = new PgEventBus(() => ['metrics'], () => true);
    expect(await bus.publish(tx as never, { ...watchChecked, dedupeKey: 'k' })).toBeNull();
    expect(calls).toHaveLength(1);
  });
});
