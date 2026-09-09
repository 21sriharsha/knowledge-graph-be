# Knowledge Platform — Backend

A Spring Boot / Java 21 **modular monolith** for a technical knowledge publishing and discovery
platform. Authors write Markdown in a Git repository; the platform materializes it into canonical
content plus everything the read, search and graph experiences need.

## The one-paragraph version

Canonical content is the source of truth. Publishing is not "saving a document" — it is a
**materialization event** that rebuilds the graph edges, the search index, the embeddings, the read
models and the caches derived from it. Search is deterministic Java: a small language model may
interpret a query into a typed `SearchIntent`, but it never generates SQL, selects records or ranks
results.

## Running it

### 1. A PostgreSQL with pgvector

The application expects `knowledge/knowledge@localhost:5432/knowledge`, which is what
`application.yaml` defaults to.

**Using a system PostgreSQL** (the documented path — verified on 17 and 18.4). As a superuser:

```sql
CREATE ROLE knowledge LOGIN PASSWORD 'knowledge';
CREATE DATABASE knowledge OWNER knowledge;
\connect knowledge
CREATE EXTENSION IF NOT EXISTS vector;      -- not a trusted extension: superuser only
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;
GRANT ALL ON SCHEMA public TO knowledge;    -- Liquibase's changelog tables live here
```

The migration re-issues those `CREATE EXTENSION IF NOT EXISTS` statements, which are then no-ops the
unprivileged role can execute.

**Or, if you have no system PostgreSQL**, `compose.yaml` provides one on **5433** — deliberately not
5432, so it cannot collide with a system instance:

```bash
docker compose up -d
export DATABASE_URL=jdbc:postgresql://localhost:5433/knowledge
```

### 2. The backend

```bash
./mvnw spring-boot:run -pl platform-app -Dspring-boot.run.profiles=local
```

Or, against a packaged jar:

```bash
./mvnw package && java -jar platform-app/target/platform-app-*.jar --spring.profiles.active=local
```

Liquibase runs the migrations at startup. Studio credentials in the `local` profile are
`author/author` and `admin/admin`.

The profile is not optional. Without it the application refuses to start, because
`knowledge.source.credential-key` has no value and provider tokens would otherwise be stored
unencrypted. `local` supplies a fixed, deliberately recognisable dev key; anywhere real it comes from
the environment. Do not make `local` the default profile to avoid passing the flag — a deployed
instance that forgot to set a profile would then quietly come up with the dev key and `author/author`
as a valid studio login.

### 3. The frontend

The UI lives in a separate repository. Point it at the backend and start it:

```bash
KNOWLEDGE_API_URL=http://localhost:8080 \
KNOWLEDGE_STUDIO_USERNAME=author KNOWLEDGE_STUDIO_PASSWORD=author \
npm run dev
```

- API docs — <http://localhost:8080/swagger-ui.html>
- Health — <http://localhost:8080/actuator/health>

**Without a model runtime**, set `KNOWLEDGE_AI_ENABLED=false` (or just leave Ollama stopped). This is
a supported configuration, not a degraded one: search falls back to the deterministic analyzer and
no embeddings are generated. To enable AI, run Ollama and pull the two models named in
`application.yaml`.

### Tests

```bash
./mvnw test
```

Integration tests need a container runtime and **skip cleanly without one**. For rootless Podman:

```bash
systemctl --user start podman.socket
export DOCKER_HOST="unix:///run/user/$(id -u)/podman/podman.sock"
```

## Vocabulary

[docs/dictionary.md](./docs/dictionary.md) defines the words this codebase uses — slug, read model,
neighbourhood, canonical vs derived, authored vs inferred, and the rest. Most of them are
load-bearing: they mark a boundary, a lifecycle state, or a distinction the architecture depends on.
Worth reading before renaming anything.

## Module layout

Eleven Maven modules. Only `platform-app` produces a deployable artifact; the others exist so that a
boundary violation is a **compile error** rather than a code-review opinion — `platform-search`
cannot import `platform-source` because its POM does not declare it, so every cross-module
dependency is a reviewable POM diff.

```
platform-common      slug policy, content hashing, the ApiError contract
platform-jobs        JobQueue: the deferred-work boundary
platform-ai          the SLM boundary — QueryUnderstandingModel, embeddings, Spring AI adapters
platform-author      author identity
platform-content     canonical articles and the Markdown AST pipeline
platform-source      external providers behind Factory -> Strategy -> Adapter
platform-graph       knowledge-graph edges in PostgreSQL
platform-search      hybrid retrieval, planning and ranking
platform-delivery    route resolution, read models, cache interaction
platform-ingestion   the materialization pipeline (Chain of Responsibility)
platform-app         composition root, configuration, database migrations
```

Inside a module: `controller / delegate / service / repository / model{entity,dto,request,response}`,
plus a named sibling package where a pattern is the design (`source/integration`,
`ingestion/pipeline`, `search/retrieval`, `search/ranking`, `content/markdown`).

Every `@Service` is an interface plus an implementation — `Default<Name>Impl`, or
`<Strategy><Name>Impl` for strategy implementations. Logging is Lombok's `@Slf4j`. Both are recorded
in `.claude/CLAUDE.md`.

## Database

Postgres schemas are logical module boundaries, split along canonical vs derived:

| Canonical (source of truth) | Derived (rebuildable) |
|---|---|
| `author`, `content`, `source`, `ingestion` | `graph`, `search`, `read_model` |

A full rematerialization is a `TRUNCATE` of the three derived schemas followed by a re-sync. No
platform table lives in `public` — it holds Liquibase's bookkeeping and nothing else.

Migrations are release-tagged. `db/changelog/releases/v1.0.0/` owns the complete DDL script set for
that release; the next release gets its own directory rather than editing this one.

## The pipeline

```
Validate -> Parse Markdown -> Extract metadata -> Persist canonical content
   -> Update graph -> Index search -> Schedule embedding -> Build read model -> Invalidate cache
```

Handlers are ordered `@Component`s discovered by Spring, so adding a step means adding a class.
`GET /api/studio/ingestions/pipeline` reports the chain **as assembled**, not as documented.

Three properties the pipeline is built around:

- **Idempotent.** Every document is compared by content hash before any derived state is rewritten,
  so a replayed webhook is cheap rather than merely harmless.
- **Contained failures.** One malformed document is recorded as a diagnostic and the run continues.
- **Stable identity.** An article is located by `(repository, path)` before slug, so retitling a file
  updates the article instead of orphaning every inbound link.

Diagnostics are first-class: unresolved `[[links]]`, missing titles and dropped raw HTML are stored
against the run and served by `GET /api/studio/ingestions/{id}`, so "why did my article not appear"
is answerable from the product rather than a log file.

## Keeping up with reality

Two scheduled jobs exist because the happy path is not the only path.

**Source reconciliation** (`PT15M`) compares each repository's head revision against what was last
materialized. Webhooks are lossy in ways this application does not control — a provider outage during
a push, a hook never registered, a delivery dropped during a restart — and each of those is otherwise
silent and permanent. It costs one branch-head call per repository, and requests a sync only where
they differ.

**Embedding backfill** (`PT10M`) drains articles that have no current vector: ingested while AI was
off, embedded while the provider was down, or edited since. Ingestion only revisits content that has
changed, so without this an outage would cost those articles their semantic discoverability forever.
Bounded per pass, because each one is an inference call.

Both are visible through `/actuator/health`, which is where the otherwise-invisible failures surface:

- `ai` — whether AI is *switched off* (deliberate) or *switched on but unreachable* (a problem), plus
  the embedding backlog.
- `sourceSync` — repositories that have never synced, or have not synced recently.

Neither reports DOWN. A degraded subsystem is something to investigate, not a reason to pull a
healthy instance out of a load balancer.

> **Before running more than one replica:** `@Scheduled` fires on every instance, so all of them
> would reconcile and backfill. It is harmless today — the work is only *requested*, the job queue
> deduplicates it, and the pipeline is idempotent — but it wastes provider and inference calls, and
> worsens with each replica. The fix is ShedLock (`shedlock-spring` +
> `shedlock-provider-jdbc-template`): the lock lives in a PostgreSQL table the platform already has,
> so it adds no infrastructure. Recorded in BACKEND-SPEC §4.9 and marked TODO on both schedulers.

## Notable decisions

**Wiki links are parsed, not regex-matched.** `[[Target]]` is recognised by a commonmark
`LinkProcessor`, so a reference inside a fenced block or a code span is an example, not a link — an
article documenting this syntax does not generate links to its own examples.

**The backend emits no HTML.** Article bodies are a typed `ContentBlock` tree. That is the layering
rule and also the sanitisation boundary: raw HTML is dropped during conversion and reported, rather
than forwarded for the frontend to escape correctly.

**Unresolved links are kept, not discarded.** A reference to an article that does not exist yet is
stored `UNRESOLVED` and repaired automatically when the target is published — without re-ingesting
the referring article.

**Retrieval scores are normalized before they are combined.** `ts_rank_cd` is unbounded above and
cosine similarity is capped at 1; adding them raw would let the lexical scale dominate for reasons
unrelated to relevance. Ranking weights are configuration, and every hit carries a score explanation.

**The analyzer chooses which interpreter runs, not whether one runs.** A query that does not warrant
an inference call is still parsed deterministically, so `by:harsh tag:pgvector` is honoured with no
model involved. (This started as a bug — the bypass path threw the filters away and matched the
syntax as free text — and the search integration test is what caught it.)

**Provider credentials are AES-256-GCM ciphertext.** No endpoint returns a stored token, and the
`RepositoryDescriptor` that carries a decrypted one overrides `toString()` to keep it out of logs.

## What is deliberately not here

No microservices, Kafka, Redis, Neo4j, Elasticsearch, or a Python inference service. Each has a
seam ready for it — `JobQueue`, the cache abstraction, `QueryUnderstandingModel`, `SourceAdapter` —
so the replacement stays a local change when evidence justifies one.
