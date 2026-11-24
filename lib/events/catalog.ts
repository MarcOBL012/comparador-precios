import { z } from 'zod';

/**
 * Catálogo de eventos de dominio: un esquema por tipo. Convención `<agregado>.<hecho_en_pasado>`
 * (la base lo exige con `event_type ~ '^[a-z_]+\.[a-z_]+$'`). Cargas útiles chicas y sin datos
 * sensibles: nunca imágenes, tokens ni correos.
 *
 * Los esquemas son estrictos: un campo de más o de menos es un error del productor y se detecta al
 * publicar, no al consumir. Cada fase del plan agrega aquí los tipos que empieza a emitir.
 */
const uuid = z.string().uuid();
const nullablePrice = z.number().positive().nullable();

export const EVENT_SCHEMAS = {
  /**
   * Una revisión de precios terminó. Los campos opcionales los publica la revisión por eventos
   * (`recordObservations`): con ellos el `AlertEvaluator` decide las alertas de TODOS los que siguen
   * el producto sin volver a consultar nada. El modo anterior (`recordCheck`) publica solo los dos primeros.
   */
  'watch.checked': z
    .object({
      ofertas: z.number().int().nonnegative(),
      descartadas: z.number().int().nonnegative(),
      productId: uuid.optional(),
      /** La mejor oferta guardada en esta revisión; null si no hubo ninguna. */
      mejor: z
        .object({ observationId: z.number().int().positive(), precio: z.number().positive(), tienda: z.string().min(1) })
        .strict()
        .nullable()
        .optional(),
      /** Historial del producto ANTES de esta revisión (las reglas R5 y R6 lo comparan con la oferta nueva). */
      statsPrevias: z
        .object({ minimo: nullablePrice, observaciones: z.number().int().nonnegative(), mediana: nullablePrice })
        .strict()
        .optional(),
    })
    .strict(),
  'watch.created': z.object({ watchId: uuid, productId: uuid, precioObjetivo: nullablePrice }).strict(),
  'watch.target_changed': z.object({ watchId: uuid, productId: uuid, anterior: nullablePrice, nuevo: nullablePrice }).strict(),
  'watch.paused': z.object({ watchId: uuid }).strict(),
  'watch.reactivated': z.object({ watchId: uuid }).strict(),
  'watch.deleted': z.object({ watchId: uuid, productId: uuid }).strict(),
  /** El scheduler pide revisar un seguimiento vencido (userId es el id interno, no un dato personal). */
  'watch.check_requested': z.object({ watchId: uuid, userId: uuid, motivo: z.literal('programada') }).strict(),
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
