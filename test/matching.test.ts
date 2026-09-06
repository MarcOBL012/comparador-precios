import { describe, it, expect } from 'vitest';
import { pickBestMatch } from '../lib/matching';
import type { ProductIdentification } from '../lib/productIdentification';

const identification: ProductIdentification = {
  marca: 'Gloria',
  nombre: 'Leche evaporada',
  presentacion: '400g',
  categoria: 'abarrotes',
  confianza: 0.9,
};

describe('pickBestMatch', () => {
  it('elige el candidato con mayor similitud por encima del umbral', () => {
    const candidates = [
      { productName: 'Leche Evaporada Ideal 400g', brand: 'Ideal' },
      { productName: 'Leche Evaporada Gloria 400g', brand: 'Gloria' },
    ];

    const result = pickBestMatch(identification, candidates);

    expect(result).toEqual(candidates[1]);
  });

  it('devuelve null si ningún candidato supera el umbral', () => {
    const candidates = [{ productName: 'Detergente Ariel 800g', brand: 'Ariel' }];

    expect(pickBestMatch(identification, candidates)).toBeNull();
  });

  it('devuelve null si no hay candidatos', () => {
    expect(pickBestMatch(identification, [])).toBeNull();
  });

  it('ignora mayúsculas y acentos al comparar', () => {
    const candidates = [{ productName: 'LECHE EVAPORADA GLÓRIA 400G', brand: 'GLORIA' }];

    const result = pickBestMatch(identification, candidates);

    expect(result).toEqual(candidates[0]);
  });

  it('conserva campos extra del candidato en el resultado', () => {
    const candidates = [
      { productName: 'Leche Evaporada Gloria 400g', brand: 'Gloria', precio: 4.5, url: 'https://example.pe/p/1' },
    ];

    const result = pickBestMatch(identification, candidates);

    expect(result).toEqual(candidates[0]);
  });
});
