import { EVENT_TYPES, type EventType } from '../catalog.js';
import type { Consumer, StoredEvent } from '../types.js';

/**
 * Escucha todos los tipos y deja un registro JSON por evento (buscable en los logs de Vercel).
 * Es best-effort y sin efectos sobre el negocio: sirve de primer consumidor para comprobar que el
 * bus entrega, y de base para las métricas de la Fase 6.
 */
export class MetricsRecorder implements Consumer {
  readonly name = 'metrics';
  readonly handles: readonly EventType[] = EVENT_TYPES;

  constructor(
    private readonly log: (line: string) => void = (line) => console.log(line),
    private readonly now: () => number = Date.now
  ) {}

  async handle(event: StoredEvent): Promise<void> {
    this.log(
      JSON.stringify({
        ts: new Date(this.now()).toISOString(),
        nivel: 'info',
        componente: 'events.metrics',
        tipo: event.type,
        eventId: event.id,
        agregado: `${event.aggregateType}:${event.aggregateId}`,
        correlationId: event.correlationId,
        retrasoMs: this.now() - event.occurredAt.getTime(),
      })
    );
  }
}
