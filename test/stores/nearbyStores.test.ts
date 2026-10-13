import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { buscarTiendasCercanas } from '../../lib/stores/nearbyStores';

const LIMA = { lat: -12.0464, lng: -77.0428 };

function punto(friendlyName: string, distance: number, coords: [number, number] = [-77.04, -12.05]) {
  return {
    distance,
    pickupPoint: { friendlyName, address: { street: 'Av. Alfonso Ugarte', number: 'S/N', neighborhood: 'Cercado', geoCoordinates: coords } },
  };
}

function vtexResponse(items: unknown[], ok = true, status = 200) {
  return { ok, status, json: async () => ({ items }) } as Response;
}

describe('buscarTiendasCercanas', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('consulta los puntos de recojo de VTEX con las coordenadas como longitud;latitud', async () => {
    fetchMock.mockResolvedValue(vtexResponse([]));

    await buscarTiendasCercanas(['Plaza Vea'], LIMA);

    expect(fetchMock.mock.calls[0][0]).toBe(
      'https://www.plazavea.com.pe/api/checkout/pub/pickup-points?geoCoordinates=-77.0428;-12.0464&page=1&pageSize=30'
    );
  });

  it('se queda con el punto más cercano que lleva la marca de la cadena, ignorando agentes de terceros', async () => {
    fetchMock.mockResolvedValue(
      vtexResponse([
        punto('PICKIT NOVAFARMA (CERCADO)', 0.2),
        punto('plazaVea - Alfonso Ugarte', 0.238, [-77.04271, -12.04855]),
        punto('plazaVea - Centro Cívico', 1.22),
      ])
    );

    const result = await buscarTiendasCercanas(['Plaza Vea'], LIMA);

    expect(result).toEqual([
      {
        cadena: 'Plaza Vea',
        nombre: 'plazaVea - Alfonso Ugarte',
        direccion: 'Av. Alfonso Ugarte S/N, Cercado',
        lat: -12.04855,
        lng: -77.04271,
        distanciaKm: 0.2,
      },
    ]);
  });

  it('ordena las cadenas de la más cercana a la más lejana', async () => {
    fetchMock.mockImplementation(async (url: string) =>
      url.includes('promart') ? vtexResponse([punto('PROMART SAN MIGUEL', 3.4)]) : vtexResponse([punto('OECHSLE JIRÓN DE LA UNIÓN', 1.1)])
    );

    const result = await buscarTiendasCercanas(['Promart', 'Oechsle'], LIMA);

    expect(result.map((t) => t.cadena)).toEqual(['Oechsle', 'Promart']);
  });

  it('ignora cadenas que no son VTEX (sin endpoint de tiendas) sin consultarlas', async () => {
    fetchMock.mockResolvedValue(vtexResponse([]));

    await buscarTiendasCercanas(['Falabella', 'Sodimac'], LIMA);

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('una cadena que falla no tumba a las demás', async () => {
    fetchMock.mockImplementation(async (url: string) =>
      url.includes('promart') ? vtexResponse([], false, 503) : vtexResponse([punto('OECHSLE SAN ISIDRO', 2)])
    );

    const result = await buscarTiendasCercanas(['Promart', 'Oechsle'], LIMA);

    expect(result.map((t) => t.cadena)).toEqual(['Oechsle']);
  });
});
