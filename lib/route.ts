import type { Coordenadas } from './stores/nearbyStores.js';

/**
 * Ruta para dibujar en el mapa de la app (OpenStreetMap). Usa el servidor público de FOSSGIS,
 * que sí tiene perfil a pie — el demo de OSRM (router.project-osrm.org) solo calcula en auto
 * aunque se le pida "foot", y daba rutas 5 veces más largas por calles de un sentido.
 * Servidor gratuito sin SLA: sirve para la demo, no para tráfico alto.
 */

export type ModoRuta = 'pie' | 'auto';

export interface Ruta {
  modo: ModoRuta;
  distanciaKm: number;
  minutos: number;
  /** Puntos [lat, lng] en orden, listos para una Polyline. */
  puntos: Array<[number, number]>;
}

/** Hasta esta distancia en línea recta se propone ir caminando. */
export const MAX_KM_A_PIE = 2.5;

const PERFIL: Record<ModoRuta, string> = {
  pie: 'routed-foot',
  auto: 'routed-car',
};

interface OsrmResponse {
  code?: string;
  routes?: Array<{
    distance: number;
    duration: number;
    geometry?: { coordinates?: Array<[number, number]> };
  }>;
}

export async function buscarRuta(
  origen: Coordenadas,
  destino: Coordenadas,
  modo: ModoRuta,
  timeoutMs = 8000
): Promise<Ruta | null> {
  // OSRM recibe "lng,lat;lng,lat". El segmento "driving" es fijo en este servidor: el perfil va en la ruta.
  const coords = `${origen.lng},${origen.lat};${destino.lng},${destino.lat}`;
  const url = `https://routing.openstreetmap.de/${PERFIL[modo]}/route/v1/driving/${coords}?overview=full&geometries=geojson`;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, {
      signal: controller.signal,
      // La política de uso del servidor exige identificar la aplicación.
      headers: { Accept: 'application/json', 'User-Agent': 'ComparadorPreciosPeru/1.0 (proyecto universitario)' },
    });
    if (!response.ok) {
      throw new Error(`Servidor de rutas respondió con estado ${response.status}`);
    }
    const body = (await response.json()) as OsrmResponse;
    const ruta = body.routes?.[0];
    const coordenadas = ruta?.geometry?.coordinates;
    if (body.code !== 'Ok' || !ruta || !coordenadas?.length) {
      return null;
    }
    return {
      modo,
      distanciaKm: Math.round(ruta.distance / 100) / 10,
      minutos: Math.max(1, Math.round(ruta.duration / 60)),
      puntos: coordenadas.map(([lng, lat]) => [lat, lng]),
    };
  } finally {
    clearTimeout(timeout);
  }
}
