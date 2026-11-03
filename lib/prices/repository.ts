import type { TransactionSql } from 'postgres';
import type { Sql } from '../db/client.js';
import type { WatchStatus } from '../watches/types.js';
import type {
  AlertRecord,
  AlertTypeCode,
  CheckOutcome,
  Decision,
  HistoryPoint,
  HistoryStats,
  Offer,
  PriceRepository,
  WatchRef,
} from './types.js';

interface AlertRow {
  id: string;
  tipo_codigo: AlertTypeCode;
  tipo_nombre: string;
  seguimiento_id: string;
  producto: string;
  tienda: string;
  precio: number;
  precio_objetivo: number | null;
  creada_en: Date;
  leida_en: Date | null;
}

/** "Plaza Vea" → "plaza_vea": el slug identifica la tienda sin depender de cómo la escriba cada fuente. */
export function storeSlug(nombre: string): string {
  return (
    nombre
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '_')
      .replace(/^_+|_+$/g, '') || 'tienda'
  );
}

const selectAlerts = (sql: Sql | TransactionSql) => sql`
  select a.id::text as id, a.alert_type_code as tipo_codigo, t.nombre as tipo_nombre,
         w.id as seguimiento_id,
         trim(concat_ws(' ', b.nombre::text, p.nombre::text, nullif(p.presentacion::text, ''))) as producto,
         s.nombre as tienda, o.precio::float8 as precio, w.target_price::float8 as precio_objetivo,
         a.created_at as creada_en, a.read_at as leida_en
  from public.alerts a
  join public.alert_types t on t.code = a.alert_type_code
  join public.watches w on w.id = a.watch_id
  join public.products p on p.id = w.product_id
  left join public.brands b on b.id = p.brand_id
  join public.price_observations o on o.id = a.observation_id
  join public.store_listings l on l.id = o.listing_id
  join public.stores s on s.id = l.store_id`;

function toAlert(row: AlertRow): AlertRecord {
  return {
    id: Number(row.id),
    tipoCodigo: row.tipo_codigo,
    tipoNombre: row.tipo_nombre,
    seguimientoId: row.seguimiento_id,
    producto: row.producto,
    tienda: row.tienda,
    precio: row.precio,
    precioObjetivo: row.precio_objetivo,
    creadaEn: row.creada_en.toISOString(),
    leidaEn: row.leida_en?.toISOString() ?? null,
  };
}

export class PgPriceRepository implements PriceRepository {
  constructor(private readonly sql: Sql) {}

  async recordCheck(
    watch: WatchRef,
    offers: Offer[],
    decide: (stats: HistoryStats) => Decision
  ): Promise<CheckOutcome> {
    return this.sql.begin(async (tx) => {
      // Se bloquea la fila del seguimiento: dos revisiones simultáneas se serializan y no duplican alertas.
      const [locked] = await tx<{ status_code: WatchStatus }[]>`
        select status_code from public.watches where id = ${watch.id} for update`;

      const [stats] = await tx<{ minimo: number | null; n: number; mediana: number | null }[]>`
        select min(o.precio)::float8 as minimo, count(*)::int as n,
               (percentile_cont(0.5) within group (order by o.precio))::float8 as mediana
        from public.price_observations o
        join public.store_listings l on l.id = o.listing_id
        where l.product_id = ${watch.productId}`;

      const decision = decide({ minimo: stats.minimo, observaciones: stats.n, mediana: stats.mediana });

      const observationIds = new Map<Offer, number>();
      for (const offer of decision.keep) {
        const [store] = await tx<{ id: number }[]>`
          insert into public.stores (slug, nombre) values (${storeSlug(offer.tienda)}, ${offer.tienda})
          on conflict (slug) do update set slug = stores.slug returning id`;
        const [listing] = await tx<{ id: string }[]>`
          insert into public.store_listings (product_id, store_id, titulo, url)
          values (${watch.productId}, ${store.id}, ${offer.titulo}, ${offer.url})
          on conflict (product_id, store_id) do update set titulo = excluded.titulo, url = excluded.url
          returning id::text as id`;
        const [obs] = await tx<{ id: string }[]>`
          insert into public.price_observations (listing_id, source_code, precio)
          values (${listing.id}, ${offer.fuente}, ${offer.precio}) returning id::text as id`;
        observationIds.set(offer, Number(obs.id));
      }

      const alertIds: number[] = [];
      if (decision.mejor) {
        const observationId = observationIds.get(decision.mejor)!;
        for (const tipo of decision.alertas) {
          const [alert] = await tx<{ id: string }[]>`
            insert into public.alerts (watch_id, observation_id, alert_type_code)
            values (${watch.id}, ${observationId}, ${tipo})
            on conflict (observation_id, watch_id, alert_type_code) do nothing
            returning id::text as id`;
          if (alert) alertIds.push(Number(alert.id));
        }
      }

      const estado: WatchStatus = decision.nuevoEstado ?? locked.status_code;
      await tx`
        update public.watches set last_checked_at = now(), status_code = ${estado} where id = ${watch.id}`;

      // Outbox: el evento se guarda en la MISMA transacción; un proceso aparte lo consume después.
      await tx`
        insert into public.domain_events (aggregate_type, aggregate_id, event_type, payload)
        values ('watch', ${watch.id}, 'watch.checked',
                ${tx.json({ ofertas: decision.keep.length, descartadas: decision.descartadas })})`;
      for (const id of alertIds) {
        await tx`
          insert into public.domain_events (aggregate_type, aggregate_id, event_type, payload)
          values ('alert', ${String(id)}, 'alert.created', ${tx.json({ alertId: id, watchId: watch.id })})`;
      }

      const alertas = alertIds.length
        ? (await tx<AlertRow[]>`${selectAlerts(tx)} where a.id in ${tx(alertIds)} order by a.id`).map(toAlert)
        : [];
      return { decision, alertas, estado };
    });
  }

  async history(userId: string, watchId: string, since: Date): Promise<HistoryPoint[] | null> {
    const [owned] = await this.sql<{ product_id: string }[]>`
      select product_id from public.watches where id = ${watchId} and user_id = ${userId}`;
    if (!owned) {
      return null;
    }
    const rows = await this.sql<{ observado_en: Date; tienda: string; precio: number }[]>`
      select o.observed_at as observado_en, s.nombre as tienda, o.precio::float8 as precio
      from public.price_observations o
      join public.store_listings l on l.id = o.listing_id
      join public.stores s on s.id = l.store_id
      where l.product_id = ${owned.product_id} and o.observed_at >= ${since}
      order by o.observed_at, o.id
      limit 1000`;
    return rows.map((r) => ({ observadoEn: r.observado_en.toISOString(), tienda: r.tienda, precio: r.precio }));
  }

  async listAlerts(userId: string, query: { unread: boolean; limit: number; offset: number }) {
    const { sql } = this;
    const unread = query.unread ? sql`and a.read_at is null` : sql``;
    const rows = await sql<AlertRow[]>`
      ${selectAlerts(sql)}
      where w.user_id = ${userId} ${unread}
      order by a.created_at desc, a.id desc
      limit ${query.limit} offset ${query.offset}`;
    const [counts] = await sql<{ total: number; sin_leer: number }[]>`
      select count(*) filter (where true ${query.unread ? sql`and a.read_at is null` : sql``})::int as total,
             count(*) filter (where a.read_at is null)::int as sin_leer
      from public.alerts a join public.watches w on w.id = a.watch_id
      where w.user_id = ${userId}`;
    return { items: rows.map(toAlert), total: counts.total, sinLeer: counts.sin_leer };
  }

  async markAlert(userId: string, alertId: number, read: boolean): Promise<AlertRecord | null> {
    const updated = await this.sql`
      update public.alerts a set read_at = ${read ? this.sql`coalesce(a.read_at, now())` : null}
      from public.watches w
      where a.id = ${alertId} and w.id = a.watch_id and w.user_id = ${userId}
      returning a.id`;
    if (updated.length === 0) {
      return null;
    }
    const [row] = await this.sql<AlertRow[]>`${selectAlerts(this.sql)} where a.id = ${alertId}`;
    return toAlert(row);
  }

  async dueWatches(olderThan: Date, limit: number) {
    const rows = await this.sql<{ id: string; user_id: string }[]>`
      select id, user_id from public.watches
      where status_code = 'activa' and (last_checked_at is null or last_checked_at < ${olderThan})
      order by last_checked_at nulls first, id
      limit ${limit}`;
    return rows.map((r) => ({ id: r.id, userId: r.user_id }));
  }
}
