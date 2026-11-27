# Task log

A dated record of maintenance and housekeeping tasks: what was asked, what was done, and anything
left open. Newest entries first. Feature design decisions belong in commit messages and
`.claude/specs/BACKEND-SPEC.md`, not here.

---

## 2026-11-26

### Bug fix: studio drafts, front-matter commas, caller errors answered as 500

- **Status:** Done on branch `bugfix/studio-drafts-and-frontmatter-commas`. Not committed or merged.
- **Spec:** `.claude/specs/bugfix-studio-drafts-and-frontmatter-commas.md` (defects D1–D5, AC-1..AC-9).
- **Source:** frontend backlog `knowledge-graph-fe/docs/backend-gaps.md` (BG-21, BG-18, BG-12), plus
  D3 and D5 found while building and verifying.
- **Done:**
  - D1: the studio list uses editorial queries (any state, newest `updatedAt` first).
  - D2: `Frontmatter.first` no longer splits on commas. A refresh path repairs stored values on
    resync without adding a revision.
  - D3: an unknown sort property returns 400.
  - D5: an unknown path returns 404.
  - D4: search `page`/`size` documented.
- **Verified:** `./mvnw package` ran 320 tests, 0 failures, 74 skipped (Docker). Manual checks
  against the local app are in spec §5.
- **Open:**
  - After deploy, resync every source so truncated titles and summaries are repaired.
  - Regenerate `apim/api-spec.yaml` after merge (the operation summary and parameter descriptions
    changed).
  - Follow-up: a run that fails after `persist-content` leaves derived models stale, and resyncs
    don't heal it (spec §3).

---

## 2026-11-25

### Add Spring Boot Actuator dependency

- **Status:** No change needed. The dependency is already present.
- **Findings:**
  - `spring-boot-starter-actuator` is declared in `platform-app/pom.xml` (the composition root, the
    only deployable module).
  - Configured in `platform-app/src/main/resources/application.yaml` under `management`: exposes
    `health, info, metrics, prometheus, caches`; health details shown `when-authorized`; every
    metric is tagged `application=knowledge-platform`.
  - Prometheus registry (`micrometer-registry-prometheus`) is on the classpath.
  - Verified against the running app: `/actuator/health` → 200 (public);
    `/actuator/prometheus` → 401 (authenticated, per `SecurityConfig`).
- **Open:** If a scraper needs `/actuator/prometheus` without credentials, decide whether to allow
  it by network policy or a dedicated scrape user. Don't add a blanket `permitAll`.

### Export the OpenAPI spec for API management

- **Status:** Done.
- **Output:** `apim/api-spec.yaml`, taken unedited from the running app's `/v3/api-docs.yaml`
  (OpenAPI 3.1.0, 37 paths, 56 schemas).
- **Findings:**
  - Swagger UI is at `/swagger-ui.html`; `/v3/api-docs*` and `/swagger-ui/**` are public. A login
    prompt means the URL is wrong: an unrecognised path falls through to `anyRequest().authenticated()`.
  - `docs/openapi.yaml` (from the initial commit) is stale. It is missing 9 paths that came with the
    trending, assets, graph overview, search suggest and account features.
- **Open:**
  - Target gateway is unconfirmed. Some gateways only import OpenAPI 3.0.x.
  - Decide whether the gateway spec should exclude `/api/webhooks/**` and the studio endpoints.
  - Decide whether `docs/openapi.yaml` should be regenerated or removed in favour of `apim/`.

### Local database setup for running the app

- **Status:** Partially done; the app was then started from the IDE.
- **Findings:**
  - The server is `postgresql17` (`/opt/postgresql17`). Arch's `pgvector` package targets the main
    `postgresql` (18) and installs to `/usr/...`, so PG17 cannot see it. pgvector must be built
    with `PG_CONFIG=/opt/postgresql17/bin/pg_config`.
- **Open:** The working tree changes the `application.yaml` datasource defaults to a superuser role
  and its password. Move these into the run configuration / environment before committing.

### Stabilise `release/dev/v1.0.0` from the open PRs

- **Status:** Done. Pushed to `origin/release/dev/v1.0.0` by the user (the agent environment has no
  GitHub credentials).
- **Done:**
  - The six open branches form one linear stack; `feature/v1.0.0-ownership-enforcement` (`dad35ca`)
    contains them all. Fast-forwarded `release/dev/v1.0.0` from `e2180dc` to that tip.
  - `b80d43c`: added the missing `@EnabledIf(containerRuntimeAvailable)` guard to
    `AccountIntegrationTest`, `OwnershipIntegrationTest` and `TrendingIntegrationTest`. Without a
    container runtime they errored instead of skipping, failing the build.
  - `./mvnw verify` passed: 230 unit tests passed; 74 integration tests skipped (no Docker/Podman).
- **Open:**
  - Run the integration tests once with a container runtime. None of the PostgreSQL-backed tests
    have run yet, including those for OIDC, ownership and trending.
