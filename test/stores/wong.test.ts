import { describe, it, expect, vi, beforeEach } from 'vitest';

const { searchVtexStoreMock } = vi.hoisted(() => ({
  searchVtexStoreMock: vi.fn(),
}));

vi.mock('../../lib/stores/vtexSearch', () => ({
  searchVtexStore: searchVtexStoreMock,
}));

import { buscarWong } from '../../lib/stores/wong';
import type { ProductIdentification } from '../../lib/productIdentification';

const identification: ProductIdentification = {
  marca: 'Gloria',
  nombre: 'Leche evaporada',
  presentacion: '400g',
  categoria: 'abarrotes',
  confianza: 0.9,
};

describe('buscarWong', () => {
  beforeEach(() => {
    searchVtexStoreMock.mockReset();
  });

  it('llama a searchVtexStore con la configuración de Wong', async () => {
    searchVtexStoreMock.mockResolvedValue(null);

    await buscarWong(identification);

    expect(searchVtexStoreMock).toHaveBeenCalledWith(
      { baseUrl: 'https://www.wong.pe', storeName: 'Wong', timeoutMs: 5000 },
      identification
    );
  });

  it('devuelve lo que resuelve searchVtexStore', async () => {
    const match = { producto: 'Leche Evaporada Gloria 400g', precio: 4.6, url: 'https://www.wong.pe/p/1' };
    searchVtexStoreMock.mockResolvedValue(match);

    const result = await buscarWong(identification);

    expect(result).toEqual(match);
  });
});
