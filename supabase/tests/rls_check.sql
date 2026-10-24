-- Manual checks. Run each block in Supabase SQL Editor after 0001_init.sql.

-- 1. RLS must be true / true for the three public tables.
--    The two private tables show rls_enabled = true, rls_forced = false (on purpose).
select c.relname as table_name,
       c.relrowsecurity      as rls_enabled,
       c.relforcerowsecurity as rls_forced
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where (n.nspname = 'public'  and c.relname in ('profiles','transactions','category_rules'))
   or (n.nspname = 'private' and c.relname in ('rate_limits','user_counters'))
order by 1;

-- 2. Each public table must have 4 policies (select, insert, update, delete), role authenticated.
select tablename, count(*) as policies, array_agg(cmd order by cmd) as commands
from pg_policies
where schemaname = 'public'
  and tablename in ('profiles','transactions','category_rules')
group by tablename
order by 1;

-- 3. Any table in public with RLS OFF? (must return 0 rows)
select c.relname
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r' and not c.relrowsecurity;

-- 4. anon must have NO rights on the tables (must return 0 rows).
select table_name, privilege_type
from information_schema.role_table_grants
where table_schema = 'public' and grantee = 'anon'
  and table_name in ('profiles','transactions','category_rules');

-- 5. Triggers present (expect stamp, limit and count triggers).
select event_object_table as table_name, trigger_name, action_timing, event_manipulation
from information_schema.triggers
where event_object_schema = 'public'
  and event_object_table in ('profiles','transactions','category_rules')
order by 1, 2, 4;

-- 6. Functions: anon must not be able to run them (all false).
select has_function_privilege('anon', 'public.delete_my_data()', 'execute') as anon_delete,
       has_function_privilege('anon', 'public.server_time()',    'execute') as anon_time,
       has_function_privilege('anon', 'private.hit(text,int,int)', 'execute') as anon_hit,
       has_function_privilege('authenticated', 'public.server_time()', 'execute') as auth_time_should_be_true;
