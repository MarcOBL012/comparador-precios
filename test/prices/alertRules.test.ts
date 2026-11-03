import { describe, expect, it } from 'vitest';
import { alertMessage, decide, summarizeHistory } from '../../lib/prices/alertRules';
import type { Offer } from '../../lib/prices/types';

const offer = (precio: number, tienda = 'Plaza Vea'): Offer => ({
  tienda,
  titulo: 'Té Negro FREE TEA',
  precio,
  url: null,
  fuente: 'directa',
});
const history = (minimo: number | null, observaciones: number, mediana: number | null) => ({ minimo, observaciones, mediana });

describe('decide (reglas R4, R5, R6)', () => {
  it('R4: el mejor precio llega al objetivo → alerta y pasa a alcanzada', () => {
    const d = decide({ objetivo: 2.5, estado: 'activa', stats: history(null, 0, null), offers: [offer(2.0), offer(3.0, 'Wong')] });

    expect(d.alertas).toEqual(['objetivo_alcanzado']);
    expect(d.nuevoEstado).toBe('alcanzada');
    expect(d.mejor?.precio).toBe(2.0);
  });

  it('R4: el precio igual al objetivo también cuenta', () => {
    expect(decide({ objetivo: 2.0, estado: 'activa', stats: history(null, 0, null), offers: [offer(2.0)] }).alertas).toContain(
      'objetivo_alcanzado'
    );
  });

  it('R4: por encima del objetivo no hay alerta ni cambio de estado', () => {
    const d = decide({ objetivo: 2.0, estado: 'activa', stats: history(null, 0, null), offers: [offer(2.5)] });

    expect(d.alertas).toEqual([]);
    expect(d.nuevoEstado).toBeNull();
  });

  it('R4: un seguimiento pausado o ya alcanzado no vuelve a disparar el objetivo', () => {
    for (const estado of ['pausada', 'alcanzada'] as const) {
      expect(decide({ objetivo: 5, estado, stats: history(null, 0, null), offers: [offer(2)] }).alertas).toEqual([]);
    }
  });

  it('sin objetivo, solo avisa mínimos históricos', () => {
    const d = decide({ objetivo: null, estado: 'activa', stats: history(3.0, 5, 3.2), offers: [offer(2.9)] });

    expect(d.alertas).toEqual(['minimo_historico']);
    expect(d.nuevoEstado).toBeNull();
  });

  it('R5: no hay mínimo histórico con menos de 3 observaciones previas', () => {
    expect(decide({ objetivo: null, estado: 'activa', stats: history(3.0, 2, 3.0), offers: [offer(2.0)] }).alertas).toEqual([]);
  });

  it('R5: un precio igual al mínimo anterior no es un nuevo mínimo', () => {
    expect(decide({ objetivo: null, estado: 'activa', stats: history(3.0, 5, 3.2), offers: [offer(3.0)] }).alertas).toEqual([]);
  });

  it('puede disparar objetivo y mínimo histórico a la vez', () => {
    const d = decide({ objetivo: 3.0, estado: 'activa', stats: history(3.5, 4, 3.6), offers: [offer(2.9)] });

    expect(d.alertas).toEqual(['objetivo_alcanzado', 'minimo_historico']);
  });

  it('R6: descarta un precio atípico (< 40 % de la mediana) y no dispara alertas con él', () => {
    const d = decide({ objetivo: 1.0, estado: 'activa', stats: history(4.0, 6, 5.0), offers: [offer(1.5), offer(4.8, 'Wong')] });

    expect(d.descartadas).toBe(1);
    expect(d.keep.map((o) => o.precio)).toEqual([4.8]);
    expect(d.alertas).toEqual(['minimo_historico'].filter(() => false));
    expect(d.nuevoEstado).toBeNull();
  });

  it('R6: sin historial suficiente no se descarta nada', () => {
    const d = decide({ objetivo: null, estado: 'activa', stats: history(5.0, 2, 5.0), offers: [offer(0.5)] });

    expect(d.descartadas).toBe(0);
    expect(d.keep).toHaveLength(1);
  });

  it('sin ofertas no hay decisión', () => {
    expect(decide({ objetivo: 2, estado: 'activa', stats: history(null, 0, null), offers: [] })).toEqual({
      keep: [],
      descartadas: 0,
      mejor: null,
      alertas: [],
      nuevoEstado: null,
    });
  });
});

describe('alertMessage', () => {
  const base = { producto: 'Free Tea Frutos Rojos 500ml', tienda: 'Plaza Vea', precio: 2 };

  it('se deriva del tipo y de los datos de la observación', () => {
    expect(alertMessage({ ...base, tipoCodigo: 'objetivo_alcanzado', precioObjetivo: 2.5 })).toBe(
      'Free Tea Frutos Rojos 500ml bajó a S/ 2.00 en Plaza Vea (tu objetivo: S/ 2.50).'
    );
    expect(alertMessage({ ...base, tipoCodigo: 'minimo_historico', precioObjetivo: null })).toContain('precio más bajo registrado');
  });
});

describe('summarizeHistory', () => {
  const point = (dia: string, precio: number) => ({ observadoEn: `${dia}T12:00:00.000Z`, tienda: 'Plaza Vea', precio });

  it('calcula mínimo, máximo, promedio y tendencia a la baja', () => {
    const s = summarizeHistory([point('2026-10-01', 3.0), point('2026-10-05', 2.5), point('2026-10-10', 2.0)]);

    expect(s).toEqual({ minimo: 2.0, maximo: 3.0, promedio: 2.5, cantidad: 3, tendencia: 'baja' });
  });

  it('tendencia al alza, estable y sin datos', () => {
    expect(summarizeHistory([point('2026-10-01', 2.0), point('2026-10-10', 2.6)]).tendencia).toBe('sube');
    expect(summarizeHistory([point('2026-10-01', 2.0), point('2026-10-10', 2.01)]).tendencia).toBe('estable');
    expect(summarizeHistory([point('2026-10-01', 2.0)]).tendencia).toBe('sin_datos');
    expect(summarizeHistory([])).toMatchObject({ cantidad: 0, minimo: null, tendencia: 'sin_datos' });
  });

  it('compara el mejor precio de cada día: una tienda cara un día no parece una subida', () => {
    const s = summarizeHistory([
      point('2026-10-01', 2.0),
      { observadoEn: '2026-10-01T13:00:00.000Z', tienda: 'Wong', precio: 3.5 },
      point('2026-10-02', 2.0),
    ]);

    expect(s.tendencia).toBe('estable');
  });
});
