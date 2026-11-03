import type { Context } from '../http/router.js';
import { requireJsonBody } from '../http/router.js';
import { created, noContent, ok, type HttpResult } from '../http/respond.js';
import { CreateWatchSchema, IdSchema, ListWatchesQuerySchema, UpdateWatchSchema, parse } from './schemas.js';
import type { WatchService } from './service.js';

export const WATCHES_PATH = '/api/v1/watches';

/** Controlador: traduce HTTP ↔ servicio. No decide reglas de negocio ni toca la base. */
export class WatchController {
  constructor(private readonly service: WatchService) {}

  create = async (ctx: Context): Promise<HttpResult> => {
    const body = parse(CreateWatchSchema, requireJsonBody(ctx.body));
    const watch = await this.service.create(ctx.user.clerkId, body.producto, body.precioObjetivo ?? null);
    return created(watch, `${WATCHES_PATH}/${watch.id}`);
  };

  list = async (ctx: Context): Promise<HttpResult> => {
    const query = parse(ListWatchesQuerySchema, ctx.query, 'Parámetros de consulta inválidos');
    const { items, total } = await this.service.list(ctx.user.clerkId, query);
    return ok({ items, total, limit: query.limit, offset: query.offset });
  };

  get = async (ctx: Context): Promise<HttpResult> => {
    const id = parse(IdSchema, ctx.params.id);
    return ok(await this.service.get(ctx.user.clerkId, id));
  };

  update = async (ctx: Context): Promise<HttpResult> => {
    const id = parse(IdSchema, ctx.params.id);
    const body = parse(UpdateWatchSchema, requireJsonBody(ctx.body));
    return ok(await this.service.update(ctx.user.clerkId, id, body));
  };

  remove = async (ctx: Context): Promise<HttpResult> => {
    const id = parse(IdSchema, ctx.params.id);
    await this.service.remove(ctx.user.clerkId, id);
    return noContent();
  };
}
