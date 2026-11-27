-- Supabase Custom Access Token hook: every signed-in user gets the AUTHOR platform role.
--
-- Spec: .claude/specs/self-serve-authoring.md (D1-D3). The backend reads ONLY the claim named by
-- knowledge.security.oidc.roles-claim (default `app_roles`) and accepts ONLY roles listed in
-- knowledge.security.oidc.token-grantable-roles (default AUTHOR; ADMIN is refused at startup).
-- Putting ADMIN here therefore grants nothing.
--
-- Install: Supabase dashboard -> SQL Editor -> run this file, then
--          Authentication -> Hooks -> "Customize Access Token (JWT) Claims" -> Postgres function
--          public.custom_access_token_hook -> Enable.
-- Effect:  applies to tokens issued after enabling (sign-in or refresh, Supabase default <= 1 hour).
-- Revoke:  ban the user (Authentication -> Users -> Ban), or add an exclusion to this function.
-- Roll back: disable the hook in the dashboard; tokens lose the claim as they refresh.

create or replace function public.custom_access_token_hook(event jsonb)
returns jsonb
language plpgsql
stable
as $$
declare
  claims jsonb := event -> 'claims';
begin
  -- Top-level claim written by Supabase Auth, not user-editable (unlike user_metadata).
  claims := jsonb_set(claims, '{app_roles}', '["AUTHOR"]'::jsonb, true);
  return jsonb_set(event, '{claims}', claims);
end;
$$;

-- Only Supabase Auth may run the hook; clients must not be able to call it.
grant usage on schema public to supabase_auth_admin;
grant execute on function public.custom_access_token_hook(jsonb) to supabase_auth_admin;
revoke execute on function public.custom_access_token_hook(jsonb) from authenticated, anon, public;
