-- Lo que Supabase ya trae y las migraciones dan por hecho. Solo para el Postgres local de Docker.
create role anon nologin;
create role authenticated nologin;
create schema if not exists extensions;

-- En Supabase la crea el proyecto (activa RLS en tablas nuevas); la migración 005 le quita permisos.
create or replace function public.rls_auto_enable() returns void
language sql as $$ select 1 $$;
