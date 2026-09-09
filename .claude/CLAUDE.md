# CLAUDE.md — Backend

## Mission

Implement a Spring Boot / Java 21 modular monolith for the knowledge publishing platform.

## Non-negotiable architecture

- Spring Boot + Java 21
- PostgreSQL + pgvector
- Spring Cache + Caffeine
- in-process async execution initially
- Spring AI for AI integration
- Ollama as the initial local SLM provider
- Markdown as canonical authoring format
- REST/OpenAPI
- modular monolith
- no premature distributed infrastructure

## Module boundaries

Keep these modules conceptually isolated:

- content
- author
- source
- ingestion
- graph
- search
- ai
- delivery
- jobs

Do not create microservices.

## Source integration rules

For GitHub, GitLab and Azure DevOps:

`Factory → Strategy → Adapter → provider`

- Factory selects provider strategy.
- Strategy expresses provider-neutral application behavior.
- Adapter owns provider-specific API models and quirks.
- Never leak provider DTOs into domain code.

Adding a new provider should require a new strategy/adapter implementation, not modification of unrelated ingestion logic.

## Ingestion rules

Use Chain of Responsibility.

Handlers must be single-purpose and operate on an `IngestionContext`.

Canonical conceptual chain:

`Validate → Parse → Metadata → Links → Persist → Graph → Search → Embedding → Read Model → Cache`

Do not make the chain an unbounded god-class.

Expensive operations should be dispatched through the job abstraction.

## Async rules

Initial implementation is in-process.

Use bounded Spring `TaskExecutor`.

Jobs must be:

- idempotent
- observable
- safely retryable where appropriate

Do not add Redis/Kafka merely to implement a queue.

Keep a job abstraction so a durable implementation can be introduced later.

## Search rules

Core principle:

> SLM interprets. Search retrieves.

Search flow:

`Controller → SearchService → Query Analyzer → SearchPlanner → Retrievers → Ranker`

Use:

- PostgreSQL FTS
- pgvector
- metadata filters
- optional graph expansion

The SLM must return a validated `SearchIntent`.

Never allow the SLM to:

- generate SQL
- directly access repositories
- choose arbitrary database operations
- become the ranking engine

Simple queries should bypass the SLM when possible.

## AI rules

Use Spring AI.

Keep:

`QueryUnderstandingModel`

as the application abstraction.

Initial implementation may use:

`SpringAiQueryUnderstandingModel → Spring AI → Ollama`

The model provider must be replaceable.

Do not create a Python service unless the project later develops genuine ML-specific requirements.

## Content rules

Markdown is canonical.

Use an AST-based parser.

Support:

- frontmatter
- Markdown
- `[[Target]]`
- `[[Target|Alias]]`
- normal external links

Do not use regex as the primary document parser.

## Graph rules

Store graph relationships in PostgreSQL initially.

Distinguish:

1. explicit author link
2. topic/tag relationship
3. platform/AI suggestion

Do not introduce Neo4j in v1.

Graph queries must be bounded.

## Delivery rules

The delivery module is responsible for:

- route resolution
- read models
- navigation models
- cache interaction

Backend returns JSON read models.

Backend must not generate frontend HTML.

## Cache rules

Use Spring Cache + Caffeine.

Cache is not source of truth.

Prefer targeted invalidation after ingestion.

Never clear the entire cache for every content change unless explicitly justified.

## Persistence rules

- migrations from day one
- PostgreSQL is canonical source of truth
- transactions around canonical content changes
- stable identities for articles where possible
- revision-aware ingestion
- explicit ingestion states

## Security rules

- validate authentication/authorization
- validate source webhooks
- never log secrets/tokens
- never persist provider credentials as ordinary article data
- validate/sanitize untrusted Markdown rendering paths
- treat SLM output as untrusted and schema-validate it

## Coding rules

- prefer explicit domain/application boundaries
- avoid static utility dumping grounds
- dependency inversion at external boundaries
- constructor injection
- immutable DTOs/records where appropriate
- meaningful names over abstractions for abstraction's sake
- no premature generic frameworks
- test behavior, not implementation trivia

## Logging

Use Lombok's `@Slf4j` on the class. Never declare a logger field by hand:

```java
@Slf4j
@Service
public class DefaultArticleServiceImpl implements ArticleService { ... }
```

Do not use `System.out`, `printStackTrace`, or a manually constructed
`LoggerFactory.getLogger(...)` field.

## Service class structure

Every service is an interface plus an implementation class. Callers depend on the interface;
only the Spring configuration ever names the implementation.

Implementation naming:

- **Strategy implementations** — `<Strategy><ServiceInterface>Impl`

  ```text
  SourceIntegrationStrategy  ->  GitHubSourceIntegrationStrategyImpl
                                 GitLabSourceIntegrationStrategyImpl
                                 AzureDevOpsSourceIntegrationStrategyImpl
  ```

- **Everything else** — `Default<ServiceInterface>Impl`

  ```text
  ArticleService  ->  DefaultArticleServiceImpl
  SearchService   ->  DefaultSearchServiceImpl
  ```

This applies to `@Service` beans. Pattern collaborators that are not services -- ingestion
handlers, retrievers, the ranker, the Markdown parser, assemblers -- stay as concrete
`@Component` classes unless more than one implementation genuinely exists.

## Testing rules

For changed behavior, add the appropriate tests:

- unit
- integration
- contract
- ingestion end-to-end when relevant

Source adapters should be tested against normalized provider contracts.

## Definition of done

A backend change is not complete unless:

- module boundaries remain intact
- provider-specific concerns remain at boundaries
- migrations are included when schema changes
- async work is idempotent
- cache behavior is explicit
- API contracts are updated
- tests cover changed behavior
- observability is sufficient to diagnose failures

## Architecture evolution

Future infrastructure is allowed only when justified by measured requirements.

Possible future replacements:

`InMemoryJobQueue → DB-backed queue → distributed queue`

`Caffeine → distributed cache`

`PostgreSQL FTS/pgvector → dedicated search infrastructure`

`PostgreSQL graph → graph database`

The current design must make these evolutions possible without prematurely implementing them.
