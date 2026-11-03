import type { Sql } from '../db/client.js';
import { PG, pgCode } from '../db/client.js';
import { ConflictError } from '../errors.js';
import type { Categoria } from '../productIdentification.js';
import type { ProductInput, Watch, WatchListQuery, WatchPatch, WatchRepository, WatchStatus } from './types.js';

interface WatchRow {
  id: string;
  target_price: number | null;
  status_code: WatchStatus;
  last_checked_at: Date | null;
  created_at: Date;
  updated_at: Date;
  product_id: string;
  nombre: string;
  presentacion: string;
  marca: string | null;
  categoria: Categoria;
  tipo: string | null;
  mejor_precio: number | null;
  mejor_tienda: string | null;
  minimo_historico: number | null;
  observaciones: number;
}

/**
 * Un seguimiento con su producto y el resumen de precios. El "mejor precio actual" mira la ÚLTIMA
 * observación de cada tienda (no todo el historial); el mínimo histórico sí mira todo el historial.
 */
const selectWatch = (sql: Sql) => sql`
  select w.id, w.target_price::float8 as target_price, w.status_code, w.last_checked_at,
         w.created_at, w.updated_at, p.id as product_id, p.nombre::text as nombre,
         p.presentacion::text as presentacion, b.nombre::text as marca, c.slug as categoria,
         t.nombre::text as tipo, cur.precio as mejor_precio, cur.tienda as mejor_tienda,
         hist.minimo as minimo_historico, coalesce(hist.n, 0)::int as observaciones
  from public.watches w
  join public.products p on p.id = w.product_id
  join public.categories c on c.id = p.category_id
  left join public.brands b on b.id = p.brand_id
  left join public.product_types t on t.id = p.product_type_id
  left join lateral (
    select o.precio::float8 as precio, s.nombre as tienda
    from public.store_listings l
    join public.stores s on s.id = l.store_id
    join lateral (
      select precio from public.price_observations
      where listing_id = l.id order by observed_at desc limit 1
    ) o on true
    where l.product_id = p.id
    order by o.precio asc
    limit 1
  ) cur on true
  left join lateral (
    select min(o.precio)::float8 as minimo, count(*) as n
    from public.price_observations o
    join public.store_listings l on l.id = o.listing_id
    where l.product_id = p.id
  ) hist on true`;

function toWatch(row: WatchRow): Watch {
  return {
    id: row.id,
    producto: {
      id: row.product_id,
      marca: row.marca ?? '',
      nombre: row.nombre,
      presentacion: row.presentacion,
      categoria: row.categoria,
      tipo: row.tipo ?? '',
    },
    precioObjetivo: row.target_price,
    estado: row.status_code,
    ultimaRevision: row.last_checked_at?.toISOString() ?? null,
    creadoEn: row.created_at.toISOString(),
    actualizadoEn: row.updated_at.toISOString(),
    resumenPrecios: {
      mejorPrecio: row.mejor_precio,
      tienda: row.mejor_tienda,
      minimoHistorico: row.minimo_historico,
      observaciones: row.observaciones,
    },
  };
}

export class PgWatchRepository implements WatchRepository {
  constructor(private readonly sql: Sql) {}

  async ensureUser(clerkId: string): Promise<string> {
    const [row] = await this.sql<{ id: string }[]>`
      insert into public.app_users (clerk_id) values (${clerkId})
      on conflict (clerk_id) do update set last_seen_at = now()
      returning id`;
    return row.id;
  }

  async upsertProduct(input: ProductInput): Promise<string> {
    const { sql } = this;
    return sql.begin(async (tx) => {
      const [brand] = input.marca
        ? await tx<{ id: number }[]>`
            insert into public.brands (nombre) values (${input.marca})
            on conflict (nombre) do update set nombre = brands.nombre returning id`
        : [undefined];
      const [type] = input.tipo
        ? await tx<{ id: number }[]>`
            insert into public.product_types (nombre) values (${input.tipo})
            on conflict (nombre) do update set nombre = product_types.nombre returning id`
        : [undefined];
      const [product] = await tx<{ id: string }[]>`
        insert into public.products (brand_id, category_id, product_type_id, nombre, presentacion)
        values (${brand?.id ?? null},
                (select id from public.categories where slug = ${input.categoria}),
                ${type?.id ?? null}, ${input.nombre}, ${input.presentacion})
        on conflict (brand_id, nombre, presentacion)
        do update set product_type_id = coalesce(products.product_type_id, excluded.product_type_id)
        returning id`;
      return product.id;
    });
  }

  async insertWatch(userId: string, productId: string, precioObjetivo: number | null): Promise<string> {
    try {
      const [row] = await this.sql<{ id: string }[]>`
        insert into public.watches (user_id, product_id, target_price)
        values (${userId}, ${productId}, ${precioObjetivo})
        returning id`;
      return row.id;
    } catch (err) {
      if (pgCode(err) === PG.UNIQUE_VIOLATION) {
        throw new ConflictError('Ya sigues este producto.', 'ya_seguido');
      }
      throw err;
    }
  }

  async countActive(userId: string): Promise<number> {
    const [row] = await this.sql<{ n: number }[]>`
      select count(*)::int as n from public.watches where user_id = ${userId} and status_code = 'activa'`;
    return row.n;
  }

  async list(userId: string, query: WatchListQuery): Promise<{ items: Watch[]; total: number }> {
    const { sql } = this;
    const filter = query.estado ? sql`and w.status_code = ${query.estado}` : sql``;
    const rows = await sql<WatchRow[]>`
      ${selectWatch(sql)}
      where w.user_id = ${userId} ${filter}
      order by w.created_at desc, w.id
      limit ${query.limit} offset ${query.offset}`;
    const [{ total }] = await sql<{ total: number }[]>`
      select count(*)::int as total from public.watches w where w.user_id = ${userId} ${filter}`;
    return { items: rows.map(toWatch), total };
  }

  async find(userId: string, id: string): Promise<Watch | null> {
    const [row] = await this.sql<WatchRow[]>`
      ${selectWatch(this.sql)} where w.id = ${id} and w.user_id = ${userId}`;
    return row ? toWatch(row) : null;
  }

  async update(userId: string, id: string, patch: WatchPatch): Promise<boolean> {
    const changes: Record<string, string | number | null> = {};
    if (patch.precioObjetivo !== undefined) {
      changes.target_price = patch.precioObjetivo;
    }
    if (patch.estado !== undefined) {
      changes.status_code = patch.estado;
    }
    try {
      const rows = await this.sql`
        update public.watches set ${this.sql(changes)}
        where id = ${id} and user_id = ${userId} returning id`;
      return rows.length > 0;
    } catch (err) {
      // La base es la última defensa: el trigger rechaza una transición que el servicio no atrapó.
      if (pgCode(err) === PG.CHECK_VIOLATION) {
        throw new ConflictError('Ese cambio de estado no está permitido.', 'transicion_invalida');
      }
      throw err;
    }
  }

  async remove(userId: string, id: string): Promise<boolean> {
    const rows = await this.sql`
      delete from public.watches where id = ${id} and user_id = ${userId} returning id`;
    return rows.length > 0;
  }
}
