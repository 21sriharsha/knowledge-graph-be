# Backend Specification

## 1. Architecture

Spring Boot + Java 21 modular monolith.

Primary modules:

- content
- author
- source
- ingestion
- graph
- search
- ai
- delivery
- jobs

PostgreSQL is the source of truth.

No microservices in v1.

## 2. Technology stack

- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA / Hibernate
- PostgreSQL
- pgvector
- Liquibase
- Spring Security where authorization is required
- Spring Cache + Caffeine
- `@Async` / `TaskExecutor` / `ApplicationEventPublisher`
- Spring Scheduling where needed
- Spring AI
- Ollama initially for local SLM development
- Java Markdown parser with AST support
- Git provider APIs
- OpenAPI
- Docker Compose for local development

## 3. Core architectural principles

### Canonical content

Markdown is the canonical authoring representation.

Source provider formats are converted into a normalized `SourceFile`, then into canonical document/domain structures.

### Boundary isolation

External provider-specific models must not leak into domain/application modules.

### SLM boundary

`SLM interprets. Search retrieves.`

The SLM produces a strongly typed `SearchIntent`. It must not generate SQL or directly select database records.

### Read optimization

Publishing is a materialization event:

`Markdown → parse → validate → persist → resolve links → graph → search/embedding jobs → read models → cache invalidation`

### Infrastructure restraint

Do not introduce distributed infrastructure until required.

## 4. Module specifications

### 4.1 Content module

Owns canonical article content and metadata.

Responsibilities:

- article lifecycle
- metadata
- Markdown representation
- publication state
- revisions
- authorship association
- tags/topics association

Does not own:

- provider API access
- search ranking
- graph traversal
- HTTP-specific provider models

Core concepts:

- Article
- ArticleRevision
- Author
- Tag/Topic
- CanonicalDocument

### 4.2 Author module

Responsibilities:

- author identity
- public author profile
- author/content association
- authorization boundary for studio operations

Author search must remain conceptually independent from topic retrieval even though the search planner can combine them.

### 4.3 Source module

Supports:

- GitHub
- GitLab
- Azure DevOps

Required pattern:

`SourceIntegrationFactory → SourceIntegrationStrategy → SourceAdapter → provider API`

#### Factory

Selects the strategy from `SourceType`.

#### Strategy

Defines provider-neutral source operations and orchestration.

#### Adapter

Encapsulates provider-specific API behavior:

- authentication
- pagination
- repository metadata
- file listing
- file retrieval
- revision retrieval
- webhook registration/validation
- provider-specific errors

Domain/application code must depend on normalized contracts.

### 4.4 Ingestion module

Uses Chain of Responsibility.

Pipeline:

1. Validate
2. Parse Markdown
3. Extract metadata
4. Resolve internal links
5. Persist canonical content
6. Update graph
7. Schedule/update search index
8. Schedule embeddings
9. Build read model
10. Invalidate/update cache

Each handler has one responsibility.

Use an `IngestionContext` to pass/enrich state.

Example conceptual structure:

- `IngestionHandler`
- `ValidationHandler`
- `MarkdownParsingHandler`
- `MetadataExtractionHandler`
- `LinkResolutionHandler`
- `ContentPersistenceHandler`
- `GraphUpdateHandler`
- `SearchIndexHandler`
- `EmbeddingHandler`
- `ReadModelHandler`
- `CacheInvalidationHandler`

The chain must be extensible without rewriting existing handlers.

#### Sync vs async

Fast, deterministic operations may run inline.

Potentially expensive operations should be submitted to the in-process job executor:

- embeddings
- large indexing operations
- AI classification
- expensive repository processing

The job interface should allow a later durable implementation.

### 4.5 Graph module

Graph relationships are stored in PostgreSQL.

Initial relationship categories:

- explicit internal link
- topic/tag relationship
- platform/AI suggested relationship

Explicit Markdown links are authoritative.

Broken references must be represented as unresolved ingestion diagnostics rather than silently discarded.

The graph API should return bounded neighborhoods rather than the entire graph.

A whole-graph *sample* is also exposed, for surfaces that show the shape of the corpus rather than
one article's surroundings — the landing page and the graph explorer's entry view. It is still
bounded: `GET /api/graph/overview?maxNodes=` caps at 150, seeds from the most-linked articles so the
sample is the interesting part of the graph rather than an arbitrary slice, fills the remainder with
recent articles, and returns edges only between nodes it included. It is not, and must not become, a
paged dump of the whole graph.

Graph nodes carry the article's primary topic, so a client can group or colour by subject without a
second request per node.

Possible relational representation:

- `article_links`
- `article_tags`
- `suggested_relationships`

No Neo4j in v1.

### 4.6 Search module

Hybrid retrieval:

- PostgreSQL FTS
- pgvector
- metadata filters
- optional graph expansion
- deterministic ranking

Architecture:

`SearchController → SearchService → Query Analyzer → SearchPlanner → Retrievers → ResultRanker`

Retrievers:

- `FullTextRetriever`
- `VectorRetriever`
- `MetadataRetriever`

The result ranker combines available signals.

Author and topic dimensions remain independent inputs.

### 4.7 AI module

Uses Spring AI.

Key abstraction:

`QueryUnderstandingModel`

Conceptual contract:

`SearchIntent understand(String query)`

Initial implementation:

`SpringAiQueryUnderstandingModel → Spring AI → Ollama/local SLM`

The model/provider must be replaceable.

The SLM is optional for simple queries.

Simple query path:

`Query → direct search`

Natural-language path:

`Query → SLM → SearchIntent → SearchPlanner`

The SLM must never:

- generate SQL
- receive the entire article corpus as context
- become the retrieval engine
- be required for ingestion correctness

Embeddings may use the Spring AI embedding abstraction and execute asynchronously.

### 4.8 Delivery module

This is the former "routing" concept, expanded to **Content Delivery / Read Model**.

Responsibilities:

- URL route resolution
- precomputed article read models
- author read models
- navigation read models
- graph context needed by public pages
- cache integration

Example:

`/blog/kubernetes-networking → RouteResolver → ArticleReadModel`

Read models avoid expensive request-time reconstruction.

Do not generate HTML in Spring Boot. Return JSON read models; Next.js owns presentation.

### 4.9 Jobs module

Provides in-process asynchronous execution.

Initial mechanism:

- Spring `TaskExecutor`
- `@Async`
- bounded executor configuration
- explicit retry policy where safe

Conceptual interface:

`JobQueue.submit(Job job)`

Initial implementation:

`InMemoryJobQueue`

Future implementations may be DB-backed or distributed.

Jobs must be idempotent, particularly repository ingestion.

#### Scheduled tasks

Two recurring tasks exist alongside the job queue:

- **source reconciliation** — the polling fallback behind webhooks; compares each connected
  repository's head revision against the last one materialized.
- **embedding backfill** — drains articles that have no current vector, because the reliability
  rules allow an article to publish when embedding generation fails.

Both are `@Scheduled` and both must remain safe to run at any time: they only request work, and the
work they request goes through the same idempotent pipeline as a webhook.

**PENDING — single-instance assumption.** `@Scheduled` fires on every running instance. With more
than one replica, all of them reconcile and all of them backfill. This is currently *harmless* —
the resulting syncs deduplicate in the job queue and the pipeline is idempotent — but it is wasted
provider calls and wasted inference calls, and it scales badly with replica count.

The intended fix is **ShedLock** (`shedlock-spring` plus `shedlock-provider-jdbc-template`), not
bespoke leader election:

- it needs no new infrastructure — the lock lives in a PostgreSQL table, which the platform already
  has, and adding Redis or ZooKeeper for this would violate the infrastructure-restraint rule;
- it is per-task rather than per-instance, so reconciliation and backfill lock independently;
- it degrades correctly — a lock that cannot be acquired means another instance is already doing the
  work, which is exactly the desired outcome.

This must be in place **before the application is run with more than one replica**. Until then the
behaviour is correct, merely wasteful, and the schedulers carry a TODO pointing here.

### 4.10 Cache

Use:

`Spring Cache + Caffeine`

Cache is an optimization, never the source of truth.

Candidate caches:

- article read models
- author read models
- graph neighborhoods
- topic read models
- route resolution

After content changes, invalidate or refresh affected entries.

## 5. Persistence model

Initial relational concepts:

- authors
- articles
- article_revisions
- tags/topics
- article_tags
- article_links
- repositories/sources
- repository_files or source snapshots as required
- ingestion_runs
- ingestion_events/diagnostics
- embeddings
- suggested_relationships
- read-model persistence if required by implementation

Use database migrations from the beginning.

## 6. Content parsing

Use an AST-based Markdown parser.

The parser must support:

- frontmatter
- headings
- paragraphs
- code blocks
- standard Markdown links
- internal `[[...]]` links
- alias syntax `[[Target|display text]]`

Do not implement internal-link extraction using regex against raw Markdown as the primary parser.

## 7. Internal link resolution

Author writes:

`[[PostgreSQL Indexes]]`

The resolver maps the logical reference to the canonical article.

Resolution states should include at least:

- RESOLVED
- UNRESOLVED

Resolution should be deterministic and case/slug policy must be explicitly defined.

Renames should preserve stable internal identity where possible.

## 8. Git/source synchronization

Preferred trigger:

`Provider webhook → source validation → ingestion`

Polling may exist as fallback/reconciliation.

Webhook processing must be idempotent.

Repository sync should be revision-aware so the same revision is not unnecessarily processed twice.

Provider API failures must remain isolated inside adapters.

## 9. API design

REST + OpenAPI.

Public representative endpoints:

- `GET /api/articles/{slug}`
- `GET /api/authors/{slug}`
- `GET /api/topics/{slug}`
- `GET /api/search`
- `GET /api/graph`
- `GET /api/graph/{nodeId}`
- `GET /api/graph/overview`

Author/source endpoints:

- `GET /api/studio/sources`
- `POST /api/studio/sources`
- `GET /api/studio/ingestions/{id}`
- article management endpoints

Provider webhooks should have dedicated endpoints and verification rules.

## 10. Search intent model

Conceptual fields:

- intent
- author
- topics
- tags
- queryText
- dateRange
- searchMode

Example:

`Show me articles written by Harsh about Kubernetes networking`

becomes approximately:

- intent = ARTICLE_SEARCH
- author = Harsh
- topics = Kubernetes, networking
- queryText = Kubernetes networking
- mode = HYBRID

The exact model is an implementation contract, not an instruction to trust arbitrary model output.

Validate structured SLM output before planning retrieval.

## 11. Ranking

Ranking should be deterministic given:

- query
- corpus state
- model/version inputs
- configured weights

Potential signals:

- lexical relevance
- vector similarity
- author match
- tag/topic match
- publication state
- recency where appropriate
- graph proximity where explicitly requested/valuable

Weights should be configuration, not scattered constants.

## 12. Read model generation

Read model generation should precompute:

- article body/render representation
- author summary
- tags/topics
- related content
- graph neighborhood/context
- breadcrumbs/navigation
- previous/next navigation where applicable

A read model should be sufficient for the normal public article page without N+1 backend calls.

### Staleness has two causes, not one

A persisted read model is reusable only when it was built from the current revision of the article
*and* by the current version of the assembler. The article's content hash answers the first; a
`SCHEMA_VERSION` stored beside the payload answers the second.

Both are needed because the read model's shape changes independently of the content. When graph
nodes gained a topic field, every stored payload was still hash-current and so was served
indefinitely — the new field was simply missing from the page, and would have stayed missing until
each author happened to edit each article. Bump `ArticleReadModel.SCHEMA_VERSION` whenever a field is
added, removed or redefined in the read model or any type nested in it; payloads written under an
older version are then treated as stale and reassembled on next read.

## 13. Cache invalidation

When an article changes:

1. update canonical content
2. update affected graph edges
3. update/schedule search indexes
4. update/schedule embeddings
5. regenerate affected read models
6. invalidate/refresh affected cache keys

Dependency-aware invalidation is preferred over clearing the entire cache.

## 14. Reliability

Ingestion must be idempotent.

A failed process can be retried safely.

Use:

- unique revision identifiers
- source/repository identity
- deterministic content hashes where useful
- transaction boundaries
- explicit ingestion state
- structured diagnostics

Avoid distributed transactions in v1.

## 15. Security

- Authenticate author/studio operations.
- Authorize source and article mutations.
- Validate webhooks.
- Never persist provider secrets in plaintext.
- Keep external access tokens outside canonical article content.
- Sanitize rendered content according to the chosen Markdown renderer/security policy.
- Treat model output as untrusted input.

## 16. Observability

At minimum:

- structured application logs
- ingestion correlation IDs
- job identifiers
- provider/source identifiers
- latency/error metrics
- search latency
- SLM latency/failure metrics
- cache hit/miss metrics

OpenTelemetry is a future-friendly choice but should not force unnecessary infrastructure.

## 17. Testing

### Unit

- ingestion handlers
- link resolution
- source factory/strategies
- adapters via contract tests/mocks
- search planner
- ranking
- read-model generation
- cache key/invalidation behavior

### Integration

- PostgreSQL
- pgvector
- migration correctness
- ingestion end-to-end
- search retrieval
- webhook handling

### Contract

- source adapters against normalized contracts
- REST/OpenAPI contracts where useful

## 18. Non-goals for v1

- microservices
- Kafka
- Redis
- Neo4j
- Elasticsearch/OpenSearch
- Kubernetes
- distributed durable job queue
- custom model training
- model fine-tuning
- Python inference service
- HTML generation in Spring Boot
