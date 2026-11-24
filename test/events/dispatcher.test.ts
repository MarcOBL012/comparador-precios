import { beforeEach, describe, expect, it } from 'vitest';
import { buildRouter } from '../../lib/app';
import { ValidationError } from '../../lib/errors';
import { EventController } from '../../lib/events/controller';
import { EventDispatcher } from '../../lib/events/dispatcher';
import { InMemoryEventQueue } from '../../lib/events/memory';
import { PermanentError } from '../../lib/events/retryPolicy';
import { ConsumerRegistry } from '../../lib/events/subscriptions';
import type { Consumer, StoredEvent } from '../../lib/events/types';

const event = (id: number, aggregateId = 'w1'): StoredEvent => ({
  id,
  type: 'watch.checked',
  version: 1,
  aggregateType: 'watch',
  aggregateId,
  payload: { ofertas: 1, descartadas: 0 },
  occurredAt: new Date(0),
  correlationId: null,
  causationId: null,
  actor: 'system',
});

class Spy implements Consumer {
  readonly handles = ['watch.checked'] as const;
  readonly seen: number[] = [];
  constructor(
    readonly name: string,
    private readonly behavior: (e: StoredEvent, call: number) => Promise<void> | void = () => {}
  ) {}
  async handle(e: StoredEvent): Promise<void> {
    this.seen.push(e.id);
    await this.behavior(e, this.seen.length);
  }
}

let now: number;
let queue: InMemoryEventQueue;
const clock = () => now;

const dispatcherFor = (consumers: Consumer[], options: ConstructorParameters<typeof EventDispatcher>[2] = {}) =>
  new EventDispatcher(queue, new ConsumerRegistry(consumers), { now: clock, random: () => 0.5, log: () => {}, ...options });

beforeEach(() => {
  now = 1_700_000_000_000;
  queue = new InMemoryEventQueue(clock);
});

describe('despachador de eventos', () => {
  it('entrega un evento a su consumidor y lo marca como hecho', async () => {
    const spy = new Spy('a');
    queue.enqueue(event(1), ['a']);
    const summary = await dispatcherFor([spy]).run();
    expect(spy.seen).toEqual([1]);
    expect(summary).toMatchObject({ reclamadas: 1, hechas: 1, reintentos: 0, muertas: 0 });
    expect(queue.row(1, 'a').status).toBe('done');
  });

  it('un fallo transitorio se reintenta recién cuando pasa la espera (30 s)', async () => {
    const spy = new Spy('a', (_e, call) => {
      if (call === 1) throw new Error('503 de la tienda');
    });
    queue.enqueue(event(1), ['a']);
    const dispatcher = dispatcherFor([spy]);

    expect((await dispatcher.run()).reintentos).toBe(1);
    expect(queue.row(1, 'a')).toMatchObject({ status: 'failed', attempts: 1 });
    expect(queue.row(1, 'a').lastError).toMatch(/503/);

    expect((await dispatcher.run()).reclamadas).toBe(0); // todavía no toca
    now += 29_000;
    expect((await dispatcher.run()).reclamadas).toBe(0);
    now += 1_000;
    expect((await dispatcher.run()).hechas).toBe(1);
    expect(queue.row(1, 'a')).toMatchObject({ status: 'done', attempts: 2 });
  });

  it('tras 6 intentos fallidos la entrega pasa a la cola de fallos con la espera creciendo', async () => {
    const spy = new Spy('a', () => {
      throw new Error('sigue caído');
    });
    queue.enqueue(event(1), ['a']);
    const dispatcher = dispatcherFor([spy]);
    const waits: number[] = [];
    for (let intento = 1; intento <= 6; intento++) {
      await dispatcher.run();
      const row = queue.row(1, 'a');
      if (row.status === 'failed') {
        waits.push((row.nextAttemptAt - now) / 1000);
        now = row.nextAttemptAt;
      }
    }
    expect(waits).toEqual([30, 60, 120, 240, 480]);
    expect(queue.row(1, 'a')).toMatchObject({ status: 'dead', attempts: 6 });
    expect((await dispatcherFor([spy]).run()).reclamadas).toBe(0); // un muerto no se vuelve a intentar solo
  });

  it('un error permanente va directo a la cola de fallos, sin reintentos', async () => {
    queue.enqueue(event(1), ['a', 'b']);
    const a = new Spy('a', () => {
      throw new PermanentError('payload imposible');
    });
    const b = new Spy('b', () => {
      throw new ValidationError('dato inválido');
    });
    const summary = await dispatcherFor([a, b]).run();
    expect(summary).toMatchObject({ muertas: 2, reintentos: 0 });
    expect(queue.row(1, 'a')).toMatchObject({ status: 'dead', attempts: 1 });
    expect(queue.row(1, 'b').status).toBe('dead');
  });

  it('un consumidor que falla no afecta a los demás del mismo evento', async () => {
    const bueno = new Spy('bueno');
    const malo = new Spy('malo', () => {
      throw new Error('boom');
    });
    queue.enqueue(event(1), ['bueno', 'malo']);
    const summary = await dispatcherFor([bueno, malo]).run();
    expect(summary).toMatchObject({ hechas: 1, reintentos: 1 });
    expect(queue.row(1, 'bueno').status).toBe('done');
    expect(queue.row(1, 'malo').status).toBe('failed');
  });

  it('no toca las entregas de un consumidor que no conoce: quedan intactas para quien sí lo conoce', async () => {
    // Otro código puede compartir la base (vista previa de Vercel, despliegue a medias). Reclamarlas las
    // marcaría como fallidas y gastaría sus intentos hasta mandarlas a la cola de fallos sin culpa del evento.
    queue.enqueue(event(1), ['fantasma', 'a']);
    const spy = new Spy('a');
    const summary = await dispatcherFor([spy]).run();
    expect(summary).toMatchObject({ reclamadas: 1, hechas: 1 });
    expect(queue.row(1, 'fantasma')).toMatchObject({ status: 'pending', attempts: 0, lastError: null });

    // Cuando aparece un despachador que sí conoce al consumidor, la procesa normalmente.
    const fantasma = new Spy('fantasma');
    await dispatcherFor([fantasma]).run();
    expect(fantasma.seen).toEqual([1]);
    expect(queue.row(1, 'fantasma').status).toBe('done');
  });

  it('pedir consumidores que no conoce tampoco reclama nada', async () => {
    queue.enqueue(event(1), ['fantasma']);
    const summary = await dispatcherFor([new Spy('a')]).run({ consumers: ['fantasma'] });
    expect(summary.reclamadas).toBe(0);
    expect(queue.row(1, 'fantasma').attempts).toBe(0);
  });

  it('respeta el orden por agregado: no entrega el evento 2 mientras el 1 siga sin terminar', async () => {
    const spy = new Spy('a', (e, call) => {
      if (e.id === 1 && call === 1) throw new Error('falla la primera vez');
    });
    queue.enqueue(event(1, 'w1'), ['a']);
    queue.enqueue(event(2, 'w1'), ['a']);
    queue.enqueue(event(3, 'w2'), ['a']); // otro agregado: no depende del 1
    const dispatcher = dispatcherFor([spy]);

    await dispatcher.run();
    expect(spy.seen).toEqual([1, 3]); // el 2 espera al 1
    expect(queue.row(2, 'a').status).toBe('pending');

    now += 30_000;
    await dispatcher.run();
    expect(spy.seen).toEqual([1, 3, 1, 2]);
    expect(queue.row(2, 'a').status).toBe('done');
  });

  it('un evento muerto no bloquea a los siguientes del mismo agregado', async () => {
    const spy = new Spy('a', (e) => {
      if (e.id === 1) throw new PermanentError('inválido');
    });
    queue.enqueue(event(1), ['a']);
    queue.enqueue(event(2), ['a']);
    await dispatcherFor([spy]).run();
    expect(queue.row(1, 'a').status).toBe('dead');
    expect(queue.row(2, 'a').status).toBe('done');
  });

  it('un consumidor colgado se corta por tiempo y cuenta como fallo transitorio', async () => {
    const colgado = new Spy('a', () => new Promise<void>(() => {}));
    queue.enqueue(event(1), ['a']);
    const summary = await dispatcherFor([colgado], { handlerTimeoutMs: 20 }).run();
    expect(summary.reintentos).toBe(1);
    expect(queue.row(1, 'a').lastError).toMatch(/superó 20 ms/);
  });

  it('recupera una entrega cuya función murió: al vencer el arrendamiento se reclama de nuevo', async () => {
    queue.enqueue(event(1), ['a']);
    await queue.claim(10, 60); // alguien la reclamó y murió sin confirmar
    const spy = new Spy('a');
    expect((await dispatcherFor([spy]).run()).reclamadas).toBe(0); // el arrendamiento sigue vigente
    now += 61_000;
    const summary = await dispatcherFor([spy]).run();
    expect(summary.hechas).toBe(1);
    expect(queue.row(1, 'a')).toMatchObject({ status: 'done', attempts: 2 });
  });

  it('deja de reclamar cuando se acaba el presupuesto de tiempo de la corrida', async () => {
    const lento = new Spy('a', () => {
      now += 10_000; // cada entrega «tarda» 10 s
    });
    for (let id = 1; id <= 10; id++) queue.enqueue(event(id, `w${id}`), ['a']);
    // presupuesto 45 s con tope por entrega de 20 s: deja de reclamar al pasar 25 s
    const summary = await dispatcherFor([lento], { batchSize: 1, budgetMs: 45_000, handlerTimeoutMs: 20_000 }).run();
    expect(summary.hechas).toBe(3);
    expect(queue.rows.filter((r) => r.status === 'pending')).toHaveLength(7);
  });

  it('si no puede registrar el resultado (la base cayó) no revienta: queda para el arrendamiento', async () => {
    const spy = new Spy('a');
    queue.enqueue(event(1), ['a']);
    queue.ack = async () => {
      throw new Error('connection terminated');
    };
    const summary = await dispatcherFor([spy]).run();
    expect(summary).toMatchObject({ hechas: 1, sinRegistrar: 1 });
    expect(queue.row(1, 'a').status).toBe('processing');
  });

  it('repone las entregas muertas con requeue y las estadísticas lo reflejan', async () => {
    const spy = new Spy('a', () => {
      throw new PermanentError('x');
    });
    queue.enqueue(event(1), ['a']);
    queue.enqueue(event(2, 'w2'), ['a']);
    await dispatcherFor([spy]).run();
    expect(await queue.stats()).toMatchObject({ muertos: 2, porConsumidor: { a: { dead: 2 } } });

    expect(await queue.requeue({ eventId: 1 })).toBe(1);
    expect(queue.row(1, 'a')).toMatchObject({ status: 'pending', attempts: 0 });
    expect(await queue.requeue({ consumer: 'a' })).toBe(1); // solo queda muerta la del evento 2
    expect(await queue.stats()).toMatchObject({ muertos: 0, porConsumidor: { a: { pending: 2 } } });
  });
});

describe('despacho acotado y concurrencia', () => {
  it('con filtros solo reclama lo pedido: una petición y ciertos consumidores', async () => {
    const a = new Spy('a');
    const b = new Spy('b');
    queue.enqueue({ ...event(1, 'w1'), correlationId: 'req-1' }, ['a', 'b']);
    queue.enqueue({ ...event(2, 'w2'), correlationId: 'req-2' }, ['a', 'b']);

    const summary = await dispatcherFor([a, b]).run({ correlationId: 'req-1', consumers: ['a'] });

    expect(a.seen).toEqual([1]);
    expect(b.seen).toEqual([]);
    expect(summary.hechasPorConsumidor).toEqual({ a: 1 });
    expect(queue.row(1, 'b').status).toBe('pending');
    expect(queue.row(2, 'a').status).toBe('pending');
  });

  it('cuenta las entregas hechas por consumidor', async () => {
    queue.enqueue(event(1, 'w1'), ['a', 'b']);
    queue.enqueue(event(2, 'w2'), ['a']);
    const summary = await dispatcherFor([new Spy('a'), new Spy('b')]).run();
    expect(summary.hechasPorConsumidor).toEqual({ a: 2, b: 1 });
  });

  it('nunca ejecuta más entregas a la vez que la concurrencia indicada', async () => {
    let active = 0;
    let peak = 0;
    const spy = new Spy('a', async () => {
      active += 1;
      peak = Math.max(peak, active);
      await new Promise((resolve) => setTimeout(resolve, 5));
      active -= 1;
    });
    for (let id = 1; id <= 12; id++) queue.enqueue(event(id, `w${id}`), ['a']);

    const summary = await dispatcherFor([spy], { batchSize: 12, concurrency: 3 }).run();

    expect(summary.hechas).toBe(12);
    expect(peak).toBe(3);
  });

  it('con un presupuesto corto (despacho en línea) procesa al menos una tanda y se detiene', async () => {
    const spy = new Spy('a');
    for (let id = 1; id <= 10; id++) queue.enqueue(event(id, `w${id}`), ['a']);
    const summary = await dispatcherFor([spy], { batchSize: 4 }).run({ budgetMs: 100 });
    expect(summary.hechas).toBe(4);
    expect(queue.rows.filter((r) => r.status === 'pending')).toHaveLength(6);
  });
});

describe('endpoints internos de eventos', () => {
  const secret = 'secreto-de-prueba';
  const build = (enabled: boolean) => {
    const dispatcher = dispatcherFor([new Spy('a')]);
    return buildRouter({ watches: {} as never, events: new EventController(dispatcher, queue, () => enabled) });
  };
  const call = (router: ReturnType<typeof buildRouter>, method: string, path: string, withSecret = true) =>
    router.dispatch({ method, path, query: {}, headers: withSecret ? { authorization: `Bearer ${secret}` } : {}, body: undefined });

  beforeEach(() => {
    process.env.CRON_SECRET = secret;
  });

  it('exigen el secreto del cron', async () => {
    const router = build(true);
    expect((await call(router, 'GET', '/internal/events/dispatch', false)).status).toBe(401);
    expect((await call(router, 'GET', '/internal/events/stats', false)).status).toBe(401);
  });

  it('con EVENTS_DISPATCH apagado no tocan la base y lo dicen', async () => {
    const router = build(false);
    expect((await call(router, 'POST', '/internal/events/dispatch')).body).toEqual({ activo: false });
    expect((await call(router, 'GET', '/internal/events/stats')).body).toEqual({ activo: false });
  });

  it('con EVENTS_DISPATCH activo despachan y devuelven el resumen y las estadísticas', async () => {
    queue.enqueue(event(1), ['a']);
    const router = build(true);
    expect((await call(router, 'POST', '/internal/events/dispatch')).body).toMatchObject({ activo: true, reclamadas: 1, hechas: 1 });
    expect((await call(router, 'GET', '/internal/events/stats')).body).toMatchObject({ activo: true, muertos: 0, porConsumidor: { a: { done: 1 } } });
  });

  it('rechaza métodos no permitidos', async () => {
    expect((await call(build(true), 'PUT', '/internal/events/dispatch')).status).toBe(405);
  });
});
