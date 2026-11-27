# Knowledge Platform --- Project North Star

**Document purpose:** This is the bird's-eye-view document for the
entire project.\
**Audience:** AI coding agents, developers, architects, reviewers, and
future maintainers.\
**Status:** Architectural north star. Feature-specific specifications
must be interpreted in the context of this document.

------------------------------------------------------------------------

## 1. What We Are Building

We are building a **technical knowledge publishing and discovery
platform** that combines the strengths of:

-   a modern technical publishing platform,
-   a Markdown-first authoring workflow,
-   an Obsidian-like interconnected knowledge base,
-   a Wikipedia-like knowledge/discovery experience,
-   and intelligent natural-language search.

The platform is not merely a blog or CMS.

The core idea is:

> **Authors publish durable technical knowledge as interconnected
> content, and readers discover that knowledge through excellent
> reading, search, and graph-based exploration.**

The system should make a piece of knowledge useful in isolation while
becoming substantially more valuable when connected to other pieces of
knowledge.

The product therefore has three primary experiences:

1.  **Read** --- consume a high-quality article or knowledge page.
2.  **Discover** --- find relevant knowledge through search, topics,
    authors, tags, and recommendations.
3.  **Explore** --- navigate relationships between concepts and articles
    through the knowledge graph.

------------------------------------------------------------------------

# 2. Product Vision

The long-term product should feel like a **personal/public technical
knowledge network**, rather than a collection of disconnected blog
posts.

A user should be able to:

-   read a technically rich article;
-   follow links to related concepts;
-   inspect an article's author, topics, and tags;
-   discover related articles;
-   search using ordinary language;
-   ask questions such as "What has Harsh written about Kubernetes
    networking?";
-   move from one concept to another through the graph;
-   understand why search results are relevant;
-   and eventually explore the knowledge base as a connected system.

Authors should be able to maintain content naturally using Markdown and
source-control workflows rather than being forced into a heavy CMS
authoring experience.

------------------------------------------------------------------------

# 3. Core Product Principle

The most important architectural/product principle is:

> **Content is the source of truth; derived representations exist to
> make that content easier to discover and serve.**

Canonical content flows through a materialization pipeline:

``` text
Markdown
   ↓
Parse
   ↓
Validate
   ↓
Persist canonical content
   ↓
Resolve internal links
   ↓
Update knowledge graph
   ↓
Update search representation
   ↓
Generate embeddings
   ↓
Build read models
   ↓
Update / invalidate caches
```

Publishing an article is therefore not just "saving a document."

It is a **materialization process** that produces all representations
required by the read and discovery experiences.

------------------------------------------------------------------------

# 4. High-Level System

``` text
                         ┌───────────────────────┐
                         │        Reader         │
                         └───────────┬───────────┘
                                     │
                                     ▼
                         ┌───────────────────────┐
                         │       Next.js         │
                         │   SSR-first frontend  │
                         └───────────┬───────────┘
                                     │ REST
                                     ▼
                 ┌─────────────────────────────────────────┐
                 │             Spring Boot                  │
                 │             Modular Monolith             │
                 │                                         │
                 │  Content                                 │
                 │  Author                                  │
                 │  Source                                  │
                 │  Ingestion                               │
                 │  Graph                                   │
                 │  Search                                  │
                 │  AI                                      │
                 │  Content Delivery / Read Models          │
                 │  Jobs                                    │
                 │  Cache                                   │
                 └───────────────────┬─────────────────────┘
                                     │
                    ┌────────────────┼─────────────────┐
                    │                │                 │
                    ▼                ▼                 ▼
              PostgreSQL          pgvector           Ollama
              + FTS               vectors            via Spring AI
```

The backend owns application/domain behavior.

The frontend owns presentation.

PostgreSQL is the primary data store and search system.

Spring AI provides the AI integration boundary.

Ollama is the initial local model runtime.

------------------------------------------------------------------------

# 5. Technology Direction

## Frontend

-   Next.js
-   TypeScript
-   App Router
-   Server Components by default
-   SSR-first public experience
-   Tailwind CSS
-   shadcn/ui where appropriate
-   React Flow or equivalent for graph visualization

## Backend

-   Spring Boot
-   Java 21
-   Modular monolith
-   REST APIs
-   Spring Data JPA / Hibernate
-   Spring Validation
-   Spring Security
-   Spring Actuator
-   Flyway

## Database

-   PostgreSQL
-   pgvector
-   PostgreSQL Full Text Search

## AI

-   Spring AI
-   Ollama for local development initially
-   Small instruction-following model for query understanding
-   Spring AI embedding abstraction for embeddings

## Cache

-   Spring Cache
-   Caffeine

## Background Processing

Initially:

-   Spring `@Async`
-   Spring `TaskExecutor`

Do not introduce distributed messaging merely for architectural fashion.

------------------------------------------------------------------------

# 6. The Architectural Shape

The backend is intentionally a **modular monolith**.

The initial conceptual modules are:

``` text
content
author
source
ingestion
graph
search
ai
delivery
jobs
cache
```

Modules should have clear ownership and boundaries.

Do not organize the entire application as one global technical-layer
hierarchy such as:

``` text
controller/
service/
repository/
entity/
dto/
```

Instead, prefer module ownership:

``` text
search/
    api/
    application/
    domain/
    retrieval/
    understanding/

content/
    api/
    application/
    domain/
    persistence/
```

The exact package layout may evolve, but module boundaries should remain
explicit.

------------------------------------------------------------------------

# 7. Module Responsibilities

## Content

Owns the canonical knowledge content.

Responsibilities include:

-   articles/pages;
-   Markdown-derived document structure;
-   publication state;
-   slugs;
-   metadata;
-   topics;
-   tags;
-   content revisions/version information where required;
-   relationships to authors and sources.

Content is canonical.

Search indexes, embeddings, graph projections, and read models are
derived.

------------------------------------------------------------------------

## Author

Owns author identity and author-facing metadata.

Examples:

-   author profile;
-   display name;
-   biography;
-   avatar/reference metadata;
-   authored content relationships.

The author module should not own article content.

------------------------------------------------------------------------

## Source

Owns external source definitions.

Initial integrations may include:

-   GitHub;
-   GitLab;
-   Bitbucket;
-   Azure DevOps.

Use a stable abstraction so the application does not become coupled to
one provider.

Preferred design:

``` text
SourceFactory
      ↓
SourceStrategy
      ↓
ProviderAdapter
```

The application should depend on source capabilities rather than
provider-specific implementations.

------------------------------------------------------------------------

## Ingestion

Owns the transformation of external/source content into canonical
platform content.

The pipeline should conceptually be:

``` text
Source
  ↓
Fetch
  ↓
Detect changes
  ↓
Parse Markdown
  ↓
Validate
  ↓
Persist
  ↓
Resolve links
  ↓
Update graph
  ↓
Update search representation
  ↓
Generate embeddings
  ↓
Build read models
  ↓
Update/invalidate cache
```

Use a **Chain of Responsibility** or equivalent composable processing
pipeline.

Ingestion must be idempotent.

A repeated webhook or synchronization event must not create duplicate
content or corrupt derived state.

------------------------------------------------------------------------

## Graph

The knowledge graph represents relationships between knowledge entities.

For v1, graph storage is PostgreSQL.

Do not introduce Neo4j unless actual requirements demonstrate that
PostgreSQL is no longer sufficient.

Primary relationships include:

``` text
Article ──links_to──> Article
Article ──authored_by──> Author
Article ──tagged_with──> Tag
Article ──belongs_to──> Topic
```

Internal Markdown links such as:

``` text
[[Kubernetes Networking]]
```

must be resolved into stable content relationships.

The graph is both:

-   a navigation/discovery feature;
-   and a potential retrieval signal for search.

------------------------------------------------------------------------

# 8. Search Is a First-Class Product Capability

Search is not simply a SQL `LIKE` query.

The search system should support:

-   exact/keyword retrieval;
-   PostgreSQL Full Text Search;
-   semantic/vector retrieval;
-   metadata filters;
-   author filtering;
-   topic filtering;
-   tag filtering;
-   date filtering;
-   graph-aware expansion where useful;
-   combined ranking.

The architecture is:

``` text
SearchController
      ↓
SearchService
      ↓
QueryUnderstandingModel
      ↓
SearchIntent
      ↓
SearchPlanner
      ↓
 ┌───────────────┬────────────────┬─────────────────┐
 │               │                │
 ▼               ▼                ▼
FTS          Vector Search     Metadata/Graph
 │               │                │
 └───────────────┴────────────────┘
                 ↓
            ResultRanker
                 ↓
           SearchResponse
```

------------------------------------------------------------------------

# 9. The Critical AI Principle

The AI system must not become the search system.

The core principle is:

> **SLM interprets. Search retrieves.**

The SLM's job is to understand the user's query.

The application's job is to retrieve and rank the actual data.

For example:

User query:

> "What has Harsh written about Kubernetes that is related to
> networking?"

The SLM should produce a structured intent approximately like:

``` json
{
  "intent": "ARTICLE_SEARCH",
  "author": "Harsh",
  "topics": ["Kubernetes", "networking"],
  "tags": [],
  "queryText": "Kubernetes networking",
  "mode": "HYBRID"
}
```

Java code then decides how that intent maps to:

-   author filters;
-   PostgreSQL FTS;
-   vector similarity;
-   graph expansion;
-   ranking.

The model must never generate SQL.

The model must never directly access the database.

The model must never be trusted as the source of truth.

------------------------------------------------------------------------

# 10. AI Boundary

The search domain should depend on an abstraction such as:

``` java
public interface QueryUnderstandingModel {
    SearchIntent understand(String query);
}
```

The search application should know about `SearchIntent`, not about
Ollama internals.

This allows the implementation to evolve:

``` text
QueryUnderstandingModel
        │
        ├── Ollama implementation
        ├── hosted model implementation
        ├── alternative provider
        └── mock/test implementation
```

Spring AI is the integration mechanism.

For local development:

``` text
Spring Boot
    ↓
Spring AI
    ↓
Ollama
    ↓
Local SLM
```

------------------------------------------------------------------------

# 11. SLM Should Be Optional

Not every search requires an LLM.

Simple queries should be able to use deterministic search directly.

Conceptually:

``` text
Simple query
    ↓
Direct search
```

while:

``` text
Natural-language query
    ↓
Query understanding
    ↓
SearchIntent
    ↓
SearchPlanner
    ↓
Retrieval
```

This keeps latency, cost, and operational complexity under control.

------------------------------------------------------------------------

# 12. Search Planner

The Search Planner is deliberately separate from the SLM.

The SLM answers:

> "What does the user mean?"

The planner answers:

> "How should the system retrieve it?"

For example:

``` text
SearchIntent
    ↓
SearchPlanner
    ├── author filter
    ├── topic filter
    ├── tag filter
    ├── PostgreSQL FTS
    ├── pgvector similarity
    └── graph expansion
```

This separation is a major architectural invariant.

Do not collapse query understanding and retrieval into one AI-generated
operation.

------------------------------------------------------------------------

# 13. Read Models and Content Delivery

Public pages are read-heavy.

The backend should therefore prepare **read models** rather than forcing
every request to reconstruct the entire page from normalized tables.

Conceptually:

``` text
URL
 ↓
Route Resolver
 ↓
Read Model
 ↓
Next.js SSR
 ↓
HTML
```

An article read model might contain:

``` text
article
author
tags
topics
relatedArticles
breadcrumbs
prevArticle
nextArticle
graphContext
```

The backend prepares data.

Next.js renders the UI.

The backend must not generate HTML for the frontend.

------------------------------------------------------------------------

# 14. Cache vs Read Model

These are different concepts.

A cache answers:

> "Can I avoid hitting the database?"

A read model answers:

> "Can I avoid expensive work during the request?"

Both are useful.

The intended read path is:

``` text
Next.js SSR
     ↓
Spring Boot
     ↓
Cache
     ├── HIT → Read Model
     │
     └── MISS
           ↓
       PostgreSQL
           ↓
       Read Model
           ↓
         Cache
```

After content changes:

``` text
Source / Webhook
       ↓
   Ingestion
       ↓
 Content updated
       ↓
Read models regenerated
       ↓
Relevant caches invalidated
```

Caffeine is sufficient for the initial architecture.

Do not introduce Redis without a demonstrated requirement.

------------------------------------------------------------------------

# 15. Markdown Is Canonical

Markdown is the authoring format.

The platform should parse Markdown into an AST/structured
representation.

Do not make rendered HTML the canonical content representation.

The system should preserve enough structure to support:

-   headings;
-   paragraphs;
-   code blocks;
-   links;
-   internal links;
-   lists;
-   tables;
-   images;
-   metadata/front matter where applicable.

The Markdown AST is especially important for:

-   internal link extraction;
-   graph construction;
-   validation;
-   search text extraction;
-   rendering-related metadata;
-   future transformations.

------------------------------------------------------------------------

# 16. External Source Integration

The platform is designed to ingest content from source-control/content
providers.

The source integration layer should isolate provider differences.

Preferred conceptual architecture:

``` text
Source
  ↓
SourceFactory
  ↓
SourceStrategy
  ↓
ProviderAdapter
  ↓
Normalized Source Content
  ↓
Ingestion Pipeline
```

A GitHub-specific detail should not leak into the content domain.

The same content should be ingestible regardless of whether it
originated from GitHub, GitLab, Bitbucket, or Azure DevOps.

------------------------------------------------------------------------

# 17. Publishing Is a Materialization Event

When content changes, the system should think in terms of rebuilding the
derived knowledge representation.

``` text
Article changed
      ↓
Canonical content updated
      ↓
Internal links recalculated
      ↓
Graph relationships updated
      ↓
Search representation updated
      ↓
Embedding generated/updated
      ↓
Read models rebuilt
      ↓
Caches invalidated
```

This gives the system a clean mental model:

> **Canonical content is authoritative; everything else is materialized
> from it.**

------------------------------------------------------------------------

# 18. Frontend Philosophy

The frontend is optimized primarily for reading and discovery.

Public content is:

-   SSR-first;
-   fast;
-   accessible;
-   SEO-friendly;
-   highly linkable;
-   progressively enhanced.

Use React Server Components by default.

Client components should exist where interaction genuinely requires
them, such as:

-   search interaction;
-   graph exploration;
-   interactive filters;
-   editor/studio interactions.

Do not turn the entire application into a client-side SPA unnecessarily.

------------------------------------------------------------------------

# 19. Core User Journeys

## Reader

``` text
Landing page
    ↓
Discover/search
    ↓
Article
    ↓
Related content
    ↓
Graph
    ↓
Another article
```

## Search

``` text
Search box
    ↓
Query
    ↓
Query understanding when useful
    ↓
Hybrid retrieval
    ↓
Ranked results
    ↓
Article
```

## Author

``` text
Author connects source
    ↓
Source synchronization
    ↓
Content detected
    ↓
Markdown parsed
    ↓
Content validated
    ↓
Content published
    ↓
Graph/search/read models updated
```

------------------------------------------------------------------------

# 20. Performance Philosophy

The public reading path should be optimized for low latency.

Prefer:

-   SSR;
-   precomputed read models;
-   cache-aside;
-   database indexes;
-   PostgreSQL-native search;
-   asynchronous embedding generation;
-   asynchronous/non-blocking ingestion work where appropriate.

Do not make every page request:

-   call an LLM;
-   calculate graph relationships from scratch;
-   rebuild complex joins unnecessarily;
-   generate embeddings synchronously.

Expensive derived work belongs in the materialization/background
pipeline.

------------------------------------------------------------------------

# 21. Reliability Philosophy

The system should degrade gracefully.

Examples:

### AI unavailable

Search should still work using deterministic retrieval.

``` text
SLM unavailable
    ↓
Fallback to direct/hybrid search
```

### Embedding generation fails

The article should still be publishable.

The system can retry embedding generation asynchronously.

### Source provider temporarily unavailable

Do not corrupt existing canonical content.

Record synchronization failure and retry.

### Cache unavailable

The application should continue functioning by reading from PostgreSQL.

The cache is an optimization, not the source of truth.

------------------------------------------------------------------------

# 22. Security Philosophy

Security must be enforced at the backend boundary.

The frontend must never be treated as a security boundary.

The backend owns:

-   authentication;
-   authorization;
-   source credentials/secrets;
-   publication permissions;
-   administrative operations.

Secrets must never be stored in source code.

External source credentials must be handled as secrets.

Public read APIs and author/admin APIs should have clearly separated
authorization requirements.

------------------------------------------------------------------------

# 23. Observability

The application should make important operations observable.

At minimum, be able to understand:

-   HTTP request latency;
-   database performance;
-   ingestion duration;
-   ingestion failures;
-   source synchronization status;
-   search latency;
-   SLM latency/failures;
-   embedding generation status;
-   cache hit/miss behavior;
-   read-model generation failures.

Spring Boot Actuator is part of the foundation.

Do not add a large observability stack until it is justified.

------------------------------------------------------------------------

# 24. Testing Philosophy

Tests should reflect module boundaries and business behavior.

Priorities:

1.  domain logic tests;
2.  ingestion pipeline tests;
3.  Markdown parsing/link resolution tests;
4.  search planner tests;
5.  SearchIntent parsing/validation tests;
6.  repository/database integration tests;
7.  API tests;
8.  source adapter contract tests;
9.  read-model generation tests;
10. end-to-end tests for critical user journeys.

AI-dependent behavior should be testable without requiring a live model.

Provide deterministic/mock implementations of AI abstractions.

------------------------------------------------------------------------

# 25. Architectural Non-Goals for v1

Do **not** introduce the following unless a concrete requirement forces
the decision:

-   microservices;
-   Kubernetes;
-   Kafka;
-   Redis;
-   Elasticsearch/OpenSearch;
-   Neo4j;
-   separate Python ML service;
-   distributed job infrastructure;
-   complex event-streaming architecture.

These may become valid later.

The architecture should leave room for them without requiring them now.

------------------------------------------------------------------------

# 26. Evolution Strategy

The system should have explicit abstraction boundaries where future
scale may require different infrastructure.

For example:

``` text
Cache abstraction
    ↓
Caffeine today
    ↓
Redis later if needed
```

``` text
QueryUnderstandingModel
    ↓
Ollama today
    ↓
Hosted model later
```

``` text
SourceAdapter
    ↓
GitHub/GitLab/etc.
    ↓
Additional providers later
```

``` text
Retrieval abstraction
    ↓
PostgreSQL FTS + pgvector today
    ↓
Specialized search infrastructure only if justified
```

The goal is **replaceable infrastructure, not premature
infrastructure**.

------------------------------------------------------------------------

# 27. Important Architectural Invariants

These should be treated as non-negotiable unless this document is
deliberately revised.

### Invariant 1 --- Canonical content

Markdown-derived canonical content is the source of truth.

### Invariant 2 --- Modular monolith

The backend starts as one Spring Boot application with explicit module
boundaries.

### Invariant 3 --- PostgreSQL first

PostgreSQL owns relational data, graph data, full-text search, and
vector storage for v1.

### Invariant 4 --- SLM boundary

The SLM produces structured intent.

It does not retrieve data.

### Invariant 5 --- Search planner

Java/application code determines retrieval strategy.

### Invariant 6 --- SSR-first

Next.js renders public content primarily through server-side rendering.

### Invariant 7 --- Backend owns domain behavior

The frontend must not bypass the backend or access the database
directly.

### Invariant 8 --- Derived representations

Graph projections, embeddings, search representations, read models, and
caches are derived from canonical content.

### Invariant 9 --- Async expensive work

Embedding generation, ingestion work, and other expensive derived
processing should not unnecessarily block public read requests.

### Invariant 10 --- Infrastructure follows evidence

Do not introduce distributed infrastructure until actual requirements
justify it.

------------------------------------------------------------------------

# 28. Decision-Making Rules for AI Coding Agents

When implementing any feature, an AI agent should reason in this order:

## Step 1 --- Identify the product capability

Ask:

> What user-facing capability is this change enabling?

Do not begin from the requested class/file alone.

## Step 2 --- Identify the owning module

Ask:

> Which module owns this concept?

Avoid putting logic into whichever package is easiest to reach.

## Step 3 --- Protect canonical vs derived data

Ask:

> Is this authoritative content or a derived representation?

If derived, it should be rebuildable from canonical data.

## Step 4 --- Protect module boundaries

Do not directly access another module's internals when an
application/domain boundary should exist.

## Step 5 --- Protect the read path

For public content, prefer:

``` text
precompute → cache → serve
```

rather than:

``` text
request → expensive computation → serve
```

## Step 6 --- Protect the AI boundary

If AI is involved:

``` text
User query
   ↓
AI interpretation
   ↓
Typed domain object
   ↓
Deterministic application logic
```

Never:

``` text
User query
   ↓
LLM
   ↓
SQL
   ↓
Database
```

## Step 7 --- Prefer the simplest infrastructure that satisfies the requirement

Before introducing a new dependency or infrastructure component, ask:

> Can PostgreSQL, Spring Boot, Spring AI, Caffeine, or the existing
> modular architecture already solve this?

------------------------------------------------------------------------

# 29. What "Good" Looks Like

A successful implementation should feel like one coherent system.

A reader should experience:

``` text
Fast page
  ↓
Excellent article
  ↓
Useful links
  ↓
Related knowledge
  ↓
Search
  ↓
Semantic discovery
  ↓
Knowledge graph
```

An author should experience:

``` text
Write Markdown
  ↓
Commit/publish
  ↓
Automatic ingestion
  ↓
Validation
  ↓
Graph/search/index generation
  ↓
Published knowledge
```

A developer should experience:

``` text
Clear module
  ↓
Clear domain boundary
  ↓
Clear API
  ↓
Clear persistence model
  ↓
Replaceable infrastructure
```

An AI coding agent should experience:

``` text
North Star
  ↓
Feature-specific specification
  ↓
Existing architecture
  ↓
Module boundary
  ↓
Implementation
```

------------------------------------------------------------------------

# 30. Final Mental Model

Always keep this picture in mind:

``` text
                         KNOWLEDGE PLATFORM
                                │
              ┌─────────────────┼─────────────────┐
              │                 │                 │
             READ            DISCOVER          EXPLORE
              │                 │                 │
           Next.js            Search            Graph
             SSR                │                 │
              │                 │                 │
              └─────────────────┼─────────────────┘
                                │
                         Spring Boot
                       Modular Monolith
                                │
       ┌────────────┬───────────┼───────────┬────────────┐
       │            │           │           │            │
    Content      Ingestion    Search       AI       Delivery
       │            │           │           │            │
       └────────────┴───────────┼───────────┴────────────┘
                                │
                           PostgreSQL
                         ┌──────┼──────┐
                         │      │      │
                        Data   FTS   pgvector
                         │
                        Graph

                         Spring AI
                             │
                           Ollama
                             │
                            SLM
```

The deepest product idea is:

> **Turn Markdown-based technical writing into a connected, searchable,
> intelligently discoverable knowledge system.**

The deepest architectural idea is:

> **Canonical content is the source of truth; ingestion materializes
> graph, search, embeddings, read models, and cache representations
> around it.**

The deepest AI idea is:

> **SLM interprets. Search retrieves.**

The deepest engineering idea is:

> **Keep the core simple, modular, observable, and replaceable;
> introduce infrastructure only when the product actually needs it.**

------------------------------------------------------------------------

# 31. How to Use This Document

This document should be treated as the **North Star / project context
document**.

Feature-specific specifications are more detailed and may define exact
behavior, API contracts, data models, or implementation requirements.

When a feature specification appears to conflict with this document:

1.  identify the conflict;
2.  do not silently invent a compromise;
3.  determine whether the feature genuinely requires changing the
    architecture;
4.  if it does, update the architectural decision deliberately;
5.  otherwise implement the feature within the existing North Star.

Every major implementation decision should answer:

> **Does this move the system toward the knowledge publishing and
> discovery platform described here, or is it merely solving the
> immediate ticket?**

If the latter, reconsider the design.

------------------------------------------------------------------------

**Document status:** Living architectural north star.\
**Expected relationship:** This document provides context; detailed
frontend/backend/module specifications provide implementation-level
requirements.
