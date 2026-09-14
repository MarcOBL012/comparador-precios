import { describe, it, expect, vi, beforeEach } from 'vitest';

const { searchVtexStoreMock } = vi.hoisted(() => ({
  searchVtexStoreMock: vi.fn(),
}));

vi.mock('../../lib/stores/vtexSearch', () => ({
  searchVtexStore: searchVtexStoreMock,
}));

import { buscarMetro } from '../../lib/stores/metro';
import { buscarOechsle } from '../../lib/stores/oechsle';
import { buscarPromart } from '../../lib/stores/promart';
import type { ProductQuery } from '../../lib/productIdentification';

const query: ProductQuery = { marca: 'Logitech', nombre: 'Mouse M190', presentacion: '', categoria: 'tecnologia' };

describe.each([
  { buscar: buscarMetro, baseUrl: 'https://www.metro.pe', storeName: 'Metro' },
  { buscar: buscarOechsle, baseUrl: 'https://www.oechsle.pe', storeName: 'Oechsle' },
  { buscar: buscarPromart, baseUrl: 'https://www.promart.pe', storeName: 'Promart' },
])('$storeName', ({ buscar, baseUrl, storeName }) => {
  beforeEach(() => {
    searchVtexStoreMock.mockReset();
  });

  it('delega en searchVtexStore con su configuración y devuelve su resultado', async () => {
    const match = { producto: 'Mouse LOGITECH M190 Negro', precio: 49, url: `${baseUrl}/p/1` };
    searchVtexStoreMock.mockResolvedValue(match);

    const result = await buscar(query);

    expect(searchVtexStoreMock).toHaveBeenCalledWith({ baseUrl, storeName, timeoutMs: 5000 }, query);
    expect(result).toEqual(match);
  });
});
