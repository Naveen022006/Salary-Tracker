-- Manual checks. Run each block in Supabase SQL Editor after 0001_init.sql (and 0003_lending.sql for the Lending tables).

-- 1. RLS must be true / true for the public tables (people + loan_entries exist after 0003_lending.sql).
--    The two private tables show rls_enabled = true, rls_forced = false (on purpose).
select c.relname as table_name,
       c.relrowsecurity      as rls_enabled,
       c.relforcerowsecurity as rls_forced
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where (n.nspname = 'public'  and c.relname in ('profiles','transactions','category_rules','people','loan_entries'))
   or (n.nspname = 'private' and c.relname in ('rate_limits','user_counters'))
order by 1;

-- 2. Each public table must have 4 policies (select, insert, update, delete), role authenticated.
select tablename, count(*) as policies, array_agg(cmd order by cmd) as commands
from pg_policies
where schemaname = 'public'
  and tablename in ('profiles','transactions','category_rules','people','loan_entries')
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
  and table_name in ('profiles','transactions','category_rules','people','loan_entries');

-- 5. Triggers present (expect stamp, limit and count triggers).
select event_object_table as table_name, trigger_name, action_timing, event_manipulation
from information_schema.triggers
where event_object_schema = 'public'
  and event_object_table in ('profiles','transactions','category_rules','people','loan_entries')
order by 1, 2, 4;

-- 6. Functions: anon must not be able to run them (all false).
select has_function_privilege('anon', 'public.delete_my_data()', 'execute') as anon_delete,
       has_function_privilege('anon', 'public.server_time()',    'execute') as anon_time,
       has_function_privilege('anon', 'private.hit(text,int,int)', 'execute') as anon_hit,
       has_function_privilege('authenticated', 'public.server_time()', 'execute') as auth_time_should_be_true;

-- 7. Lending (0003_lending.sql): the loan-entry owner trigger, limits and counters must exist.
--    Expect on loan_entries: a_stamp_ins, a_stamp_upd, b_limit, b_owner (insert+update), c_count_ins, c_count_del;
--    on people: a_stamp_ins, a_stamp_upd, b_limit, c_count_ins, c_count_del.
select event_object_table as table_name, trigger_name
from information_schema.triggers
where event_object_schema = 'public' and event_object_table in ('people','loan_entries')
group by 1, 2
order by 1, 2;

select column_name from information_schema.columns
where table_schema = 'private' and table_name = 'user_counters'
  and column_name in ('people_rows','loan_rows');          -- expect 2 rows

-- 8. A loan entry must not be able to point at another user's person (trigger b_owner raises 23503).
--    Manual test in the SQL editor (runs as postgres, so simulate a user first):
--      select set_config('request.jwt.claims', '{"sub":"<USER-A-UUID>","role":"authenticated"}', true);
--      set local role authenticated;
--      insert into public.loan_entries (id, person_id, kind, amount_paise, method, occurred_at)
--      values (gen_random_uuid(), '<PERSON-ID-OF-USER-B>', 'GAVE', 100, 'CASH', now());
--    -> ERROR: Person not found for this user (23503). Use begin; ... rollback; around it.

-- 9. Lending functions must not be runnable by anon (all false).
select has_function_privilege('anon', 'private.tg_loan_person_owner()', 'execute') as anon_owner_trigger,
       has_function_privilege('anon', 'private.tg_limit_loans()', 'execute')       as anon_limit_trigger;

-- 10. Anonymous access to the REST API must be refused. From a terminal (not SQL):
--      curl -i "$SUPABASE_URL/rest/v1/people?select=*" -H "apikey: $SUPABASE_ANON_KEY"
--      curl -i "$SUPABASE_URL/rest/v1/loan_entries?select=*" -H "apikey: $SUPABASE_ANON_KEY"
--     -> HTTP 401 / 403 (permission denied), never data.
