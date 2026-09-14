export interface StoreProduct {
  producto: string;
  precio: number;
  url: string;
}

export interface StoreResult {
  tienda: string;
  estado: 'encontrado' | 'no_encontrado' | 'error';
  producto?: string;
  precio?: number;
  url?: string;
  mensaje?: string;
  /** "web" = precio visto en Google Shopping, no consultado directo a la tienda. Ausente = tienda directa. */
  fuente?: 'web';
}
