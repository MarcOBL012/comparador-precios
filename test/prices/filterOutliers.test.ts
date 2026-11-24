import { describe, expect, it } from 'vitest';
import { decide, filterOutliers } from '../../lib/prices/alertRules';
import type { HistoryStats, Offer } from '../../lib/prices/types';

const oferta = (precio: number): Offer => ({ tienda: 'Plaza Vea', titulo: 'x', precio, url: null, fuente: 'directa' });
const historial = (mediana: number | null, observaciones: number): HistoryStats => ({ minimo: mediana, observaciones, mediana });

describe('filterOutliers (R6 como procesador previo a la revisión)', () => {
  it('descarta lo que cuesta menos del 40 % de la mediana, con al menos 3 observaciones previas', () => {
    const result = filterOutliers([oferta(0.9), oferta(4.9)], historial(5, 3));
    expect(result.keep.map((o) => o.precio)).toEqual([4.9]);
    expect(result.descartadas).toBe(1);
  });

  it('con menos de 3 observaciones no hay historial confiable: no descarta nada', () => {
    expect(filterOutliers([oferta(0.1)], historial(5, 2))).toEqual({ keep: [oferta(0.1)], descartadas: 0 });
    expect(filterOutliers([oferta(0.1)], historial(null, 5)).descartadas).toBe(0);
  });

  it('el límite es inclusivo: justo el 40 % se conserva', () => {
    expect(filterOutliers([oferta(2)], historial(5, 3)).keep).toHaveLength(1);
  });

  it('decide usa el mismo filtro: el resultado no cambió con la extracción', () => {
    const decision = decide({ objetivo: 1, estado: 'activa', stats: historial(5, 3), offers: [oferta(0.9), oferta(4.9)] });
    expect(decision.keep.map((o) => o.precio)).toEqual([4.9]);
    expect(decision.descartadas).toBe(1);
    // El 0.9 descartado NO dispara el objetivo; lo único que queda es el 4.9, que sí es un mínimo nuevo (< 5).
    expect(decision.alertas).toEqual(['minimo_historico']);
    expect(decision.nuevoEstado).toBeNull();
  });
});
