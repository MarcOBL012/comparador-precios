import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { buscarRuta } from '../lib/route';

const YO = { lat: -12.0464, lng: -77.0428 };
const TIENDA = { lat: -12.04855, lng: -77.04271 };

function osrm(body: unknown, ok = true, status = 200) {
  return { ok, status, json: async () => body } as Response;
}

describe('buscarRuta', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('pide la ruta a pie al servidor de FOSSGIS con las coordenadas como lng,lat', async () => {
    fetchMock.mockResolvedValue(osrm({ code: 'Ok', routes: [] }));

    await buscarRuta(YO, TIENDA, 'pie');

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(
      'https://routing.openstreetmap.de/routed-foot/route/v1/driving/-77.0428,-12.0464;-77.04271,-12.04855?overview=full&geometries=geojson'
    );
    expect(init.headers['User-Agent']).toContain('ComparadorPreciosPeru');
  });

  it('usa el perfil de auto cuando se le pide', async () => {
    fetchMock.mockResolvedValue(osrm({ code: 'Ok', routes: [] }));

    await buscarRuta(YO, TIENDA, 'auto');

    expect(fetchMock.mock.calls[0][0]).toContain('/routed-car/');
  });

  it('devuelve distancia, minutos y los puntos invertidos a lat,lng para la Polyline', async () => {
    fetchMock.mockResolvedValue(
      osrm({
        code: 'Ok',
        routes: [
          {
            distance: 269.1,
            duration: 217.2,
            geometry: { coordinates: [[-77.0428, -12.0464], [-77.0427, -12.0485]] },
          },
        ],
      })
    );

    const ruta = await buscarRuta(YO, TIENDA, 'pie');

    expect(ruta).toEqual({
      modo: 'pie',
      distanciaKm: 0.3,
      minutos: 4,
      puntos: [[-12.0464, -77.0428], [-12.0485, -77.0427]],
    });
  });

  it('devuelve null si el servidor no encuentra camino', async () => {
    fetchMock.mockResolvedValue(osrm({ code: 'NoRoute', routes: [] }));

    expect(await buscarRuta(YO, TIENDA, 'pie')).toBeNull();
  });

  it('lanza error si el servidor responde con error HTTP', async () => {
    fetchMock.mockResolvedValue(osrm({}, false, 429));

    await expect(buscarRuta(YO, TIENDA, 'pie')).rejects.toThrow('429');
  });
});
