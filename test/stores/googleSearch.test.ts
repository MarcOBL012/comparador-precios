import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { buscarLinkComoUltimoRecurso } from '../../lib/stores/googleSearch';
import type { ProductQuery } from '../../lib/productIdentification';

const query: ProductQuery = {
  marca: 'AJE',
  nombre: 'Free Tea Frutos Rojos',
  presentacion: '500ml',
  categoria: 'bebidas',
  tipo: 'té',
};
const OPTIONS = { apiKey: 'test-key', excluir: ['Plaza Vea', 'Wong', 'Metro'] };

function serperResponse(organic: unknown[], ok = true, status = 200) {
  return { ok, status, json: async () => ({ organic }) } as Response;
}

function organic(title: string, link: string) {
  return { title, link };
}

describe('buscarLinkComoUltimoRecurso', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('consulta la búsqueda web normal de Serper (no Shopping), con la frase más específica', async () => {
    fetchMock.mockResolvedValue(serperResponse([]));

    await buscarLinkComoUltimoRecurso(query, OPTIONS);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('https://google.serper.dev/search');
    expect(JSON.parse(init.body)).toEqual({ q: 'AJE Free Tea Frutos Rojos 500ml', gl: 'pe', hl: 'es' });
  });

  it('devuelve el primer resultado que calza, sin precio, marcado como fuente busqueda_web', async () => {
    fetchMock.mockResolvedValue(
      serperResponse([
        organic('Comprar Free Tea AJE Frutos Rojos 500ml - Tottus', 'https://www.tottus.com.pe/free-tea-frutos-rojos'),
      ])
    );

    const result = await buscarLinkComoUltimoRecurso(query, OPTIONS);

    expect(result).toEqual([
      {
        tienda: 'Tottus',
        estado: 'encontrado',
        producto: 'Comprar Free Tea AJE Frutos Rojos 500ml - Tottus',
        url: 'https://www.tottus.com.pe/free-tea-frutos-rojos',
        fuente: 'busqueda_web',
      },
    ]);
  });

  it('descarta marketplaces, redes sociales, sitios de contenido y tiendas ya consultadas', async () => {
    fetchMock.mockResolvedValue(
      serperResponse([
        organic('Free Tea Frutos Rojos', 'https://articulo.mercadolibre.com.pe/free-tea'),
        organic('Free Tea reseña', 'https://www.youtube.com/watch?v=abc'),
        organic('Free Tea en Facebook', 'https://www.facebook.com/freetea'),
        organic('Free Tea Frutos Rojos AJE', 'https://www.plazavea.com.pe/free-tea'),
      ])
    );

    expect(await buscarLinkComoUltimoRecurso(query, OPTIONS)).toEqual([]);
  });

  it('descarta resultados que no mencionan la marca ni el tipo de producto', async () => {
    fetchMock.mockResolvedValue(serperResponse([organic('Licuadora Oster 1.5L', 'https://www.sodimac.com.pe/licuadora')]));

    expect(await buscarLinkComoUltimoRecurso(query, OPTIONS)).toEqual([]);
  });

  it('ignora links con URL inválida en vez de fallar toda la búsqueda', async () => {
    fetchMock.mockResolvedValue(
      serperResponse([
        organic('Free Tea AJE', 'no-es-una-url'),
        organic('Free Tea AJE Frutos Rojos', 'https://www.sodimac.com.pe/free-tea'),
      ])
    );

    const result = await buscarLinkComoUltimoRecurso(query, OPTIONS);

    expect(result).toHaveLength(1);
    expect(result[0].tienda).toBe('Sodimac');
  });

  it('lanza un error si Serper responde con error', async () => {
    fetchMock.mockResolvedValue(serperResponse([], false, 500));

    await expect(buscarLinkComoUltimoRecurso(query, OPTIONS)).rejects.toThrow('Serper (búsqueda web) respondió con estado 500');
  });
});
