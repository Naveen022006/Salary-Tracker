-- Fix for projects that already ran 0001_init.sql before this fix was added to it.
-- Bug: in private.hit() the parameter "bucket" has the same name as the column
-- private.rate_limits.bucket, so every write failed with
--   42702: column reference "bucket" is ambiguous
-- Run this once in Supabase > SQL Editor. Safe to run more than once.

create or replace function private.hit(bucket text, max_hits int, window_seconds int)
returns void
language plpgsql
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  uid   uuid := auth.uid();
  w     timestamptz;
  cnt   int;
begin
  if uid is null then
    return;  -- no end-user (SQL editor / service role): nothing to limit
  end if;

  w := to_timestamp(floor(extract(epoch from now()) / window_seconds) * window_seconds);

  insert into private.rate_limits as r (user_id, bucket, window_start, hits)
  values (uid, hit.bucket, w, 1)
  on conflict (user_id, bucket, window_start)
  do update set hits = r.hits + 1
  returning r.hits into cnt;

  if cnt > max_hits then
    raise exception 'Too many requests, try again later' using errcode = 'PT429';
  end if;
end;
$$;

revoke all on function private.hit(text, int, int) from public, anon;
grant execute on function private.hit(text, int, int) to authenticated;
