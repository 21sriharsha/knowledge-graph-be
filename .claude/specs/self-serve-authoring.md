# Self-serve authoring — every signed-in user can publish

Status: **Implemented** on `feature/v2.0.0-self-serve-authoring` (2026-11-27). Not merged.
Owner decision: 2026-11-27. **Supersedes** the "a first sign-in grants nothing until an administrator
decides otherwise" rule introduced in `328a2da` (Add OIDC sign-in and an account model), for the
AUTHOR role only. ADMIN keeps that rule.

## 1. Problem

Today a signed-in person gets an account with no roles and no byline, and can do nothing until an
administrator grants AUTHOR **and** links a byline. The owner wants any signed-in user to be able to
publish, and the default role to be controlled from the identity provider (Supabase), so an existing
account also picks it up on its next sign-in.

Reading is already public (`/api/articles/**`, topics, tags, authors, search, graph, navigation are
`permitAll`) and is unchanged.

## 2. Decisions (owner, 2026-11-27)

| # | Decision | Rejected |
|---|---|---|
| D1 | The default role comes from the **access token**: a Supabase *Custom Access Token hook* adds a top-level claim `app_roles: ["AUTHOR"]` to every user's token. | Granting AUTHOR in the platform DB at first sign-in (owner wants Supabase to own the default, and existing accounts to inherit it on re-login). |
| D2 | The token can grant **AUTHOR only**. ADMIN comes only from the platform DB (bootstrap email, studio Accounts page). | Token-granted ADMIN: a mistake in the hook SQL, or a compromised provider dashboard, would mint platform administrators. |
| D3 | Revoking a bad actor happens **in Supabase** (ban the user, or exclude them in the hook). The studio "revoke AUTHOR" action no longer stops a token-granted AUTHOR. | A platform "suspend" flag (deferred). |
| D4 | The byline comes from the **signed-in user's profile**, not from Markdown. Each account with AUTHOR gets its own byline on first use, editable on the studio Profile page; every article from a repository that account owns is published under it. | Front-matter `author:` deciding the byline for owned repositories — anyone could publish as `author: Anita Rao`. |

### Why the token can be trusted for AUTHOR now, when `328a2da` refused it
`328a2da` refused token roles because Supabase's `role` claim is `authenticated` for everyone and
`user_metadata` is editable by the user it describes. Both are still ignored. The new claim is
written by a Postgres function that runs inside Supabase Auth before signing; users cannot set it,
and the backend reads **only** that configured claim, **only** for allow-listed roles.

## 3. Design

### 3.1 Token roles (platform-app)
- `knowledge.security.oidc.roles-claim` (default `app_roles`): the claim holding role names, as a
  JSON array or a single string. Matching is case-insensitive against `PlatformRole`.
- `knowledge.security.oidc.token-grantable-roles` (default `[AUTHOR]`): the only roles a token can
  grant. **Startup fails** if it contains `ADMIN` (D2 enforced in code, not by convention).
- Effective roles = platform DB roles ∪ (token roles ∩ grantable). Unknown or non-grantable values
  grant nothing and are logged at DEBUG (never WARN: every request would log).
- `role`, `user_metadata` and every other claim still grant nothing.
- No claim present (hook not enabled) → DB roles only, i.e. today's behaviour.
- Token roles are **not persisted**: they are re-evaluated on every request, so removing the role in
  Supabase takes effect when the user's current token expires (Supabase default: 1 hour).

### 3.2 Personal byline (platform-author)
- When a person's effective roles include AUTHOR and their account has no linked byline, the
  platform creates one and links it (`AccountService.ensurePersonalAuthor`).
- Name: the token's display name, else the email's local part, else "Author". Slug: slugified name,
  suffixed `-2`, `-3`, … when taken.
- It is **always a new author row**. It never matches an existing author by name or email, and it
  carries no email, so a front-matter `author_email` in an admin-managed repository can never attach
  articles to a person's byline (the identity-claiming risk `328a2da` guarded against).
- Concurrent first requests (a page makes several backend calls) link exactly one byline: the link is
  a conditional update (`where author_id is null`); a request that loses deletes the byline it
  created and uses the winner's.
- Administrators can still link/unlink bylines explicitly in the studio.

### 3.3 Attribution (platform-ingestion)
- Repository **with** an owner byline → every article from it is attributed to the owner, whatever
  its front matter says. A differing front-matter `author` is ignored (DEBUG log).
- Repository **without** an owner (administrator-managed, e.g. multi-author) → unchanged: front
  matter first, then the repository's display name.
- Non-admin users can only connect repositories they own (existing rule in `SourceDelegate`), so
  every self-served repository falls in the first case.

### 3.4 `/api/studio/me` (platform-author)
- Returns the caller's **effective** roles (what the request is actually authorized with), not only
  the DB grants; the frontend decides studio access from this. The admin Accounts list keeps showing
  DB grants (token roles are known only per request) — the studio labels them as granted roles.

### 3.5 Profile rename propagates (platform-author → platform-delivery)
- `AuthorService.updateProfile` publishes `AuthorProfileChangedEvent(authorId, slug)`.
- Delivery listens after commit: re-materializes the read models of the author's published articles
  (they embed the author's name) and calls `ReadModelCacheInvalidator.afterAuthorChanged`.
- This also fixes a pre-existing gap: profile edits never invalidated anything.

### 3.6 Supabase (operator step, not code)
`docs/supabase/custom-access-token-hook.sql` — the hook function and its grants. Enable it under
Supabase → Authentication → Hooks → Customize Access Token (JWT) Claims.

## 4. Out of scope
- A platform "suspend account" action (D3 defers moderation to Supabase).
- Rate limits / abuse reporting for self-published content (backlog).
- Regenerating `apim/api-spec.yaml` — no endpoint or schema changes (the `/me` shape is unchanged;
  only which roles it lists).
- Migrating existing articles of owned repositories to owner bylines happens on the next sync
  (`Article.reassignAuthor`), not by migration.

## 5. Acceptance criteria
- AC-1 A token with `app_roles: ["AUTHOR"]` authorizes studio endpoints for an account with no DB
  roles, and `/api/studio/me` lists `AUTHOR`.
- AC-2 `app_roles` containing `ADMIN` or unknown values grants none of them; ADMIN comes only from
  the DB.
- AC-3 Configuring `token-grantable-roles` with `ADMIN` fails at startup.
- AC-4 `role` and `user_metadata` claims never grant a role.
- AC-5 Without the claim, behaviour is unchanged (DB roles only).
- AC-6 The first request with effective AUTHOR and no byline creates and links a new byline named
  after the user; an existing author with the same name is not reused (slug gets a suffix).
- AC-7 A concurrent loser does not leave a second byline linked; it deletes its own and adopts the
  winner's.
- AC-8 Ingestion attributes every article of an owned repository to the owner byline, regardless of
  front-matter `author`; unowned repositories behave as before.
- AC-9 Renaming a byline re-materializes the read models of its published articles and invalidates
  the author's caches.
- AC-10 Public reading endpoints still need no authentication.

## 6. Verification
- Unit: `TokenRoleExtractorTest` (AC-1..AC-5), `DefaultAccountServiceImplPersonalAuthorTest`
  (AC-6, AC-7), `MetadataExtractionHandlerTest` (AC-8), `AuthorProfileChangedListenerTest` (AC-9),
  `AccountJwtAuthenticationConverterTest` (union + byline trigger).
- Manual: local backend against the owner's Supabase project with the hook enabled.

## 7. Rollback
Code-only, no migration. Disable the Supabase hook to stop granting AUTHOR (takes effect as tokens
expire). Bylines created by this feature remain and stay linked; they are ordinary author rows.
