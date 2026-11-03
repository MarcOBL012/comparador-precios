-- 005: endurecimiento tras la revisión de los asesores de Supabase.
--
-- public.rls_auto_enable() la trae el proyecto (activa RLS en tablas nuevas) y quedó invocable por
-- anon/authenticated vía /rest/v1/rpc. Un disparador de eventos no necesita EXECUTE para correr,
-- así que se quita ese acceso.
revoke execute on function public.rls_auto_enable() from public, anon, authenticated;

-- FK sin índice de cobertura (tabla chica, pero evita el aviso y mantiene la regla "toda FK indexada").
create index idx_watch_status_transitions_to_code on public.watch_status_transitions (to_code);
