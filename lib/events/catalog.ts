import { z } from 'zod';

/**
 * Catálogo de eventos de dominio: un esquema por tipo. Convención `<agregado>.<hecho_en_pasado>`
 * (la base lo exige con `event_type ~ '^[a-z_]+\.[a-z_]+$'`). Cargas útiles chicas y sin datos
 * sensibles: nunca imágenes, tokens ni correos.
 *
 * Los esquemas son estrictos: un campo de más o de menos es un error del productor y se detecta al
 * publicar, no al consumir. Cada fase del plan agrega aquí los tipos que empieza a emitir.
 */
export const EVENT_SCHEMAS = {
  /** Una revisión de precios terminó (la guarda `PriceRepository.recordCheck`). */
  'watch.checked': z
    .object({
      ofertas: z.number().int().nonnegative(),
      descartadas: z.number().int().nonnegative(),
    })
    .strict(),
  /** Se creó una alerta para un seguimiento. */
  'alert.created': z
    .object({
      alertId: z.number().int().positive(),
      watchId: z.string().uuid(),
    })
    .strict(),
} as const;

export type EventType = keyof typeof EVENT_SCHEMAS;
export type EventPayloads = { [K in EventType]: z.infer<(typeof EVENT_SCHEMAS)[K]> };

/** Versión vigente del esquema de cada tipo; sube cuando cambia de forma incompatible. */
export const EVENT_VERSION = 1;

export const EVENT_TYPES = Object.keys(EVENT_SCHEMAS) as EventType[];

export function isEventType(value: unknown): value is EventType {
  return typeof value === 'string' && Object.prototype.hasOwnProperty.call(EVENT_SCHEMAS, value);
}

/** El productor intentó publicar algo que no cumple el contrato: es un bug, no un fallo transitorio. */
export class EventContractError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'EventContractError';
  }
}

export function validatePayload<T extends EventType>(type: T, payload: unknown): EventPayloads[T] {
  if (!isEventType(type)) {
    throw new EventContractError(`Tipo de evento desconocido: ${String(type)}`);
  }
  const parsed = EVENT_SCHEMAS[type].safeParse(payload);
  if (!parsed.success) {
    const detail = parsed.error.issues.map((i) => `${i.path.join('.') || '(raíz)'}: ${i.message}`).join('; ');
    throw new EventContractError(`Carga útil inválida para ${type}: ${detail}`);
  }
  return parsed.data as EventPayloads[T];
}
