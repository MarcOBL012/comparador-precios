import { z } from 'zod';
import { ok, type HttpResult } from '../http/respond.js';
import { requireJsonBody, type Context } from '../http/router.js';
import { IdSchema, parse } from '../watches/schemas.js';
import type { PriceService } from './service.js';

const HistoryQuerySchema = z.object({ days: z.coerce.number().int().min(1).max(365).default(30) });

const ListAlertsQuerySchema = z.object({
  unread: z
    .enum(['true', 'false'])
    .default('false')
    .transform((v) => v === 'true'),
  limit: z.coerce.number().int().min(1).max(50).default(20),
  offset: z.coerce.number().int().min(0).default(0),
});

const AlertIdSchema = z.coerce.number().int().positive();
const MarkAlertSchema = z.object({ leida: z.boolean() }).strict();

export class PriceController {
  constructor(private readonly service: PriceService) {}

  check = async (ctx: Context): Promise<HttpResult> => {
    const id = parse(IdSchema, ctx.params.id);
    return ok(await this.service.check(ctx.user.clerkId, id));
  };

  history = async (ctx: Context): Promise<HttpResult> => {
    const id = parse(IdSchema, ctx.params.id);
    const { days } = parse(HistoryQuerySchema, ctx.query, 'Parámetros de consulta inválidos');
    return ok(await this.service.history(ctx.user.clerkId, id, days));
  };

  listAlerts = async (ctx: Context): Promise<HttpResult> => {
    const query = parse(ListAlertsQuerySchema, ctx.query, 'Parámetros de consulta inválidos');
    const result = await this.service.listAlerts(ctx.user.clerkId, query);
    return ok({ ...result, limit: query.limit, offset: query.offset });
  };

  markAlert = async (ctx: Context): Promise<HttpResult> => {
    const id = parse(AlertIdSchema, ctx.params.id, 'Identificador de alerta inválido');
    const { leida } = parse(MarkAlertSchema, requireJsonBody(ctx.body));
    return ok(await this.service.markAlert(ctx.user.clerkId, id, leida));
  };

  /** Disparado por el cron de Vercel (GET) o a mano (POST) con el secreto compartido. */
  checkDue = async (): Promise<HttpResult> => ok(await this.service.checkDue());
}
