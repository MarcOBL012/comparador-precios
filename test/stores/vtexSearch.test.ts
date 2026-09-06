import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { searchVtexStore } from '../../lib/stores/vtexSearch';
import type { ProductIdentification } from '../../lib/productIdentification';

const identification: ProductIdentification = {
  marca: 'Gloria',
  nombre: 'Leche evaporada',
  presentacion: '400g',
  categoria: 'abarrotes',
  confianza: 0.9,
};

const CONFIG = { baseUrl: 'https://www.example-store.pe', storeName: 'Tienda Ejemplo', timeoutMs: 5000 };

function jsonResponse(body: unknown, ok = true, status = 200) {
  return {
    ok,
    status,
    json: async () => body,
  } as Response;
}

describe('searchVtexStore', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('construye la URL de búsqueda con la marca y el nombre codificados', async () => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    await searchVtexStore(CONFIG, identification);

    expect(fetchMock).toHaveBeenCalledWith(
      'https://www.example-store.pe/api/catalog_system/pub/products/search/Gloria%20Leche%20evaporada?_from=0&_to=9',
      expect.objectContaining({ signal: expect.any(AbortSignal) })
    );
  });

  it('devuelve null si la tienda no tiene resultados', async () => {
    fetchMock.mockResolvedValue(jsonResponse([]));

    const result = await searchVtexStore(CONFIG, identification);

    expect(result).toBeNull();
  });

  it('devuelve null si el único resultado no está disponible', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse([
        {
          productName: 'Leche Evaporada Gloria 400g',
          brand: 'Gloria',
          link: 'https://www.example-store.pe/p/1',
          items: [{ sellers: [{ commertialOffer: { Price: 4.5, IsAvailable: false } }] }],
        },
      ])
    );

    const result = await searchVtexStore(CONFIG, identification);

    expect(result).toBeNull();
  });

  it('devuelve el mejor candidato disponible mapeado a producto/precio/url', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse([
        {
          productName: 'Detergente Ariel 800g',
          brand: 'Ariel',
          link: 'https://www.example-store.pe/p/0',
          items: [{ sellers: [{ commertialOffer: { Price: 12.9, IsAvailable: true } }] }],
        },
        {
          productName: 'Leche Evaporada Gloria 400g',
          brand: 'Gloria',
          link: 'https://www.example-store.pe/p/1',
          items: [{ sellers: [{ commertialOffer: { Price: 4.5, IsAvailable: true } }] }],
        },
      ])
    );

    const result = await searchVtexStore(CONFIG, identification);

    expect(result).toEqual({
      producto: 'Leche Evaporada Gloria 400g',
      precio: 4.5,
      url: 'https://www.example-store.pe/p/1',
    });
  });

  it('lanza un error descriptivo si la respuesta no es exitosa', async () => {
    fetchMock.mockResolvedValue(jsonResponse(null, false, 500));

    await expect(searchVtexStore(CONFIG, identification)).rejects.toThrow('Tienda Ejemplo respondió con estado 500');
  });

  it('lanza un error descriptivo si la respuesta no es un array', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ error: 'not found' }));

    await expect(searchVtexStore(CONFIG, identification)).rejects.toThrow(
      'Tienda Ejemplo devolvió una respuesta inesperada'
    );
  });

  it('lanza un error descriptivo si la petición es abortada por timeout', async () => {
    const abortError = new Error('The operation was aborted');
    abortError.name = 'AbortError';
    fetchMock.mockRejectedValue(abortError);

    await expect(searchVtexStore(CONFIG, identification)).rejects.toThrow(
      'Tienda Ejemplo: tiempo de espera agotado (5000ms)'
    );
  });
});
