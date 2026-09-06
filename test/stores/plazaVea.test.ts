import { describe, it, expect, vi, beforeEach } from 'vitest';

const { searchVtexStoreMock } = vi.hoisted(() => ({
  searchVtexStoreMock: vi.fn(),
}));

vi.mock('../../lib/stores/vtexSearch', () => ({
  searchVtexStore: searchVtexStoreMock,
}));

import { buscarPlazaVea } from '../../lib/stores/plazaVea';
import type { ProductIdentification } from '../../lib/productIdentification';

const identification: ProductIdentification = {
  marca: 'Gloria',
  nombre: 'Leche evaporada',
  presentacion: '400g',
  categoria: 'abarrotes',
  confianza: 0.9,
};

describe('buscarPlazaVea', () => {
  beforeEach(() => {
    searchVtexStoreMock.mockReset();
  });

  it('llama a searchVtexStore con la configuración de Plaza Vea', async () => {
    searchVtexStoreMock.mockResolvedValue(null);

    await buscarPlazaVea(identification);

    expect(searchVtexStoreMock).toHaveBeenCalledWith(
      { baseUrl: 'https://www.plazavea.com.pe', storeName: 'Plaza Vea', timeoutMs: 5000 },
      identification
    );
  });

  it('devuelve lo que resuelve searchVtexStore', async () => {
    const match = { producto: 'Leche Evaporada Gloria 400g', precio: 4.5, url: 'https://www.plazavea.com.pe/p/1' };
    searchVtexStoreMock.mockResolvedValue(match);

    const result = await buscarPlazaVea(identification);

    expect(result).toEqual(match);
  });
});
