import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { buscarEnGoogleShopping, displayName, parseSoles, queryVariants, storeKey } from '../../lib/stores/googleShopping';
import type { ProductQuery } from '../../lib/productIdentification';

const query: ProductQuery = { marca: 'Logitech', nombre: 'Mouse M190', presentacion: '', categoria: 'tecnologia' };
const OPTIONS = { apiKey: 'test-key', excluir: ['Plaza Vea', 'Oechsle', 'Promart'] };

function serperResponse(shopping: unknown[], ok = true, status = 200) {
  return { ok, status, json: async () => ({ shopping }) } as Response;
}

function offer(title: string, source: string, price: string) {
  return { title, source, price, link: `https://www.google.com/shopping/${encodeURIComponent(source)}` };
}

describe('parseSoles', () => {
  it('lee precios en soles con separador de miles', () => {
    expect(parseSoles('S/ 1,299.90')).toBe(1299.9);
    expect(parseSoles('S/. 49.00')).toBe(49);
  });

  it('descarta otras monedas o textos sin precio', () => {
    expect(parseSoles('$ 20.00')).toBeNull();
    expect(parseSoles(undefined)).toBeNull();
  });
});

describe('storeKey', () => {
  it('unifica el nombre comercial y el dominio de la misma tienda', () => {
    expect(storeKey('Falabella Perú')).toBe(storeKey('falabella.com.pe'));
    expect(storeKey('Plaza Vea')).toBe(storeKey('www.plazavea.com.pe'));
  });
});

describe('displayName', () => {
  it('prefiere el nombre comercial y le quita país y razón social', () => {
    expect(displayName(['falabella.com.pe', 'Falabella Perú'])).toBe('Falabella');
    expect(displayName(['Kingstore Peru S.A.C.'])).toBe('Kingstore');
  });

  it('convierte un dominio en nombre cuando no hay otro', () => {
    expect(displayName(['Metro.pe'])).toBe('Metro');
  });
});

describe('queryVariants', () => {
  it('no repite frases cuando presentación y tipo están vacíos', () => {
    expect(queryVariants(query)).toEqual(['Logitech Mouse M190']);
  });

  it('agrega una frase sin presentación y otra con marca + tipo, de más a menos específica', () => {
    const identification: ProductQuery = {
      marca: 'AJE',
      nombre: 'Free Tea Frutos Rojos',
      presentacion: '500ml',
      categoria: 'bebidas',
      tipo: 'te helado',
    };

    expect(queryVariants(identification)).toEqual([
      'AJE Free Tea Frutos Rojos 500ml',
      'AJE Free Tea Frutos Rojos',
      'AJE te helado',
    ]);
  });
});

describe('buscarEnGoogleShopping', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('consulta Serper con la key, la búsqueda y la región de Perú', async () => {
    fetchMock.mockResolvedValue(serperResponse([]));

    await buscarEnGoogleShopping(query, OPTIONS);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('https://google.serper.dev/shopping');
    expect(init.headers).toEqual({ 'X-API-KEY': 'test-key', 'Content-Type': 'application/json' });
    expect(JSON.parse(init.body)).toEqual({ q: 'Logitech Mouse M190', gl: 'pe', hl: 'es' });
  });

  it('devuelve una oferta por tienda, la más parecida, ordenadas de menor a mayor precio', async () => {
    fetchMock.mockResolvedValue(
      serperResponse([
        offer('Mouse Logitech M190', 'Sodimac Perú', 'S/ 51.50'),
        offer('Mouse Logitech M190 Inalámbrico', 'Falabella Perú', 'S/ 49.90'),
        offer('Combo Logitech Mouse M190 + Pad', 'falabella.com.pe', 'S/ 86.00'),
      ])
    );

    const result = await buscarEnGoogleShopping(query, OPTIONS);

    expect(result.map((r) => [r.tienda, r.precio, r.fuente])).toEqual([
      ['Falabella', 49.9, 'web'],
      ['Sodimac', 51.5, 'web'],
    ]);
  });

  it('omite marketplaces, tiendas ya consultadas directo, otras monedas y productos de otra marca', async () => {
    fetchMock.mockResolvedValue(
      serperResponse([
        offer('Mouse Logitech M190', 'mercadolibre.com.pe', 'S/ 40.00'),
        offer('Mouse Logitech M190', 'plazavea.com.pe', 'S/ 49.00'),
        offer('Mouse Logitech M190', 'Tienda USA', '$ 12.00'),
        offer('Mouse Genius DX-110', 'Hiraoka', 'S/ 19.90'),
      ])
    );

    expect(await buscarEnGoogleShopping(query, OPTIONS)).toEqual([]);
  });

  it('deja una sola oferta, la más barata, cuando dos fuentes se muestran con el mismo nombre', async () => {
    fetchMock.mockResolvedValue(
      serperResponse([
        offer('Mouse Logitech M190', 'Kingstore Peru S.A.C.', 'S/ 75.50'),
        offer('Mouse Logitech M190', 'kingstore.pe', 'S/ 70.00'),
      ])
    );

    const result = await buscarEnGoogleShopping(query, OPTIONS);

    expect(result.map((r) => [r.tienda, r.precio])).toEqual([['Kingstore', 70]]);
  });

  it('respeta el límite de ofertas', async () => {
    fetchMock.mockResolvedValue(
      serperResponse(['A', 'B', 'C', 'D'].map((store, i) => offer('Mouse Logitech M190', `Tienda ${store}`, `S/ ${50 + i}`)))
    );

    expect(await buscarEnGoogleShopping(query, { ...OPTIONS, limite: 2 })).toHaveLength(2);
  });

  it('lanza un error si Serper responde con error', async () => {
    fetchMock.mockResolvedValue(serperResponse([], false, 403));

    await expect(buscarEnGoogleShopping(query, OPTIONS)).rejects.toThrow('Serper respondió con estado 403');
  });

  it('si la frase específica no encuentra nada, prueba frases más amplias hasta encontrar un match', async () => {
    const identification: ProductQuery = {
      marca: 'AJE',
      nombre: 'Free Tea Frutos Rojos',
      presentacion: '500ml',
      categoria: 'bebidas',
      tipo: 'te helado',
    };
    fetchMock
      .mockResolvedValueOnce(serperResponse([])) // "AJE Free Tea Frutos Rojos 500ml": nada
      .mockResolvedValueOnce(serperResponse([])) // "AJE Free Tea Frutos Rojos": nada
      .mockResolvedValueOnce(
        serperResponse([offer('AJE Te Helado Frutos Rojos 500ml', 'Tottus', 'S/ 3.50')])
      ); // "AJE te helado": sí

    const result = await buscarEnGoogleShopping(identification, OPTIONS);

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(result.map((r) => [r.tienda, r.precio])).toEqual([['Tottus', 3.5]]);
  });

  it('un error puntual en una frase no impide que una frase posterior sí encuentre resultados', async () => {
    const identification: ProductQuery = {
      marca: 'AJE',
      nombre: 'Free Tea Frutos Rojos',
      presentacion: '500ml',
      categoria: 'bebidas',
      tipo: 'te helado',
    };
    fetchMock
      .mockResolvedValueOnce(serperResponse([], false, 500))
      .mockResolvedValueOnce(serperResponse([offer('AJE Te Helado Frutos Rojos 500ml', 'Tottus', 'S/ 3.50')]));

    const result = await buscarEnGoogleShopping(identification, OPTIONS);

    expect(result.map((r) => r.tienda)).toEqual(['Tottus']);
  });
});
