import type { EventType } from './catalog.js';
import type { Consumer } from './types.js';

const NAME = /^[a-z][a-z0-9_]*$/;

/**
 * Registro de suscripciones: tópico (tipo de evento) → consumidores. Es la única fuente de verdad
 * de «quién escucha qué»; el bus la usa para crear las entregas y el despachador para encontrar al
 * consumidor de cada una.
 */
export class ConsumerRegistry {
  private readonly byName = new Map<string, Consumer>();

  constructor(consumers: readonly Consumer[]) {
    for (const consumer of consumers) {
      if (!NAME.test(consumer.name)) {
        throw new Error(`Nombre de consumidor inválido: "${consumer.name}" (usa minúsculas y guion bajo).`);
      }
      if (this.byName.has(consumer.name)) {
        throw new Error(`Consumidor duplicado: "${consumer.name}".`);
      }
      this.byName.set(consumer.name, consumer);
    }
  }

  get(name: string): Consumer | undefined {
    return this.byName.get(name);
  }

  all(): Consumer[] {
    return [...this.byName.values()];
  }

  consumersFor(type: EventType): string[] {
    return this.all()
      .filter((c) => c.handles.includes(type))
      .map((c) => c.name);
  }
}
