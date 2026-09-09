# Dictionary

The vocabulary of this codebase, and what each word is doing here.

Read this before renaming anything. Most of these words are load-bearing: they mark a boundary, a
lifecycle state, or a distinction the architecture depends on, and a synonym that seems harmless in
one module usually erases a difference that matters in another.

Terms are grouped by where they live. Names in `code font` exist as types, columns or constants.

---

## The distinctions that shape everything else

### Canonical / derived

The most important pair in the codebase, and the reason the database has more than one schema.

**Canonical** data is authoritative. Losing it is data loss. It lives in the `author`, `content`,
`source` and `ingestion` schemas.

**Derived** data is materialized from canonical data by re-running the ingestion pipeline. A full
rebuild is a `TRUNCATE` of exactly the `graph`, `search` and `read_model` schemas, and nothing is
permanently lost by doing it.

The test is not "is it important" but "could we reconstruct it from the Markdown". Search indexes
and read models are extremely important and entirely derived.

**Observed** is a third kind, added later and deliberately not folded into either: `analytics` holds
view counts, which nobody authored and no rebuild can recreate. See *view* below.

### Canonical Markdown

The Markdown exactly as the author wrote it, stored on the article. Everything else — the block
tree, the search document, the read model — is produced from it. It is the reason a full rebuild is
possible.

### Read model vs entity

An **entity** (`Article`, `Author`) is canonical state, normalized, written through JPA.

A **read model** (`ArticleReadModel`, `AuthorReadModel`) is a denormalized representation shaped for
one screen, assembled once and cached. Read models are never written to by anything but the
assembler, and no business decision is ever made from one.

---

## Identity and content

### Slug

`Slug` — a normalized, URL-safe identity for an article, author, topic or tag.

It is a *type*, not a `String`, on purpose: it stops a raw title being passed where a normalized
slug is required, which would silently produce unresolvable links instead of a compile error.

A slug is the stable public identity — it appears in URLs — and it is also what a `[[Wiki Link]]`
resolves against.

### Article

The unit of publication. Carries its canonical Markdown, its slug, a title, an author, taxonomy, and
a `PublicationState`.

### Publication state

`PublicationState` — `DRAFT`, `PUBLISHED`, `ARCHIVED`.

`ARCHIVED` is not deletion. An archived article is retained so that inbound links are not orphaned;
the platform prefers a visibly withdrawn article over a dangling reference.

### Revision

`ArticleRevision` — a prior version of an article's content, kept when ingestion replaces it.
Ingestion is *revision-aware*: it knows whether the incoming document is new, changed, or the same.

### Content hash

`contentHash` — a digest of an article's canonical content. It answers exactly one question: **is
this stored thing built from the current version of the source?**

Used in three places, all staleness checks: skipping unchanged documents during ingestion, detecting
a stale search index, and invalidating a stored read model.

It cannot answer whether a *representation* is current, only whether the *content* is — which is why
read models carry a second marker. See *schema version*.

### Schema version

`ArticleReadModel.SCHEMA_VERSION` — which shape of read-model payload a stored record was written
under.

Needed because a read model's shape changes independently of the article. Without it, a payload
built by an older assembler stays content-hash-current and is served indefinitely, silently missing
whatever field was added.

### Frontmatter

`Frontmatter` — the YAML block at the top of a Markdown file. Author-supplied metadata: `title`,
`summary`, `slug`, `topics`, `tags`, `author`, `draft`, `published`.

Every key is optional; each has a documented fallback. See `documentation/authors.md`.

### Canonical document

`CanonicalDocument` — the output of parsing one Markdown file: title, summary, block tree, outline,
frontmatter, extracted links. The parser's product, before anything is persisted.

### Content block / inline content

`ContentBlock` — the article body as a tree, not as HTML: `Heading`, `Paragraph`, `CodeBlock`,
`Quote`, `ListBlock`, `ListItem`, `TableBlock`, `TableRow`, `TableCell`, `ThematicBreak`.

The backend emits no markup at all. Sending a block tree is what keeps untrusted Markdown from
making the frontend a security boundary.

### Outline

`DocumentHeading` — the article's headings, flattened with their levels. Feeds the "on this page"
navigation without re-parsing the body.

### Topic vs tag

Both are taxonomy, and they are not interchangeable.

A **topic** is a broad subject. An article's *first* topic is its **primary topic**, which drives its
breadcrumb and colours its node in the graph.

A **tag** is a narrower label. An article has many, with no primary.

---

## Links and the graph

### Extracted link vs edge

`ExtractedLink` is a link *as found in a document*, before resolution. An edge (`ArticleLink`) is a
resolved connection between two articles.

They are separate types because the content module extracts and the graph module resolves. That
separation is what lets a link to a not-yet-written article be recorded as unresolved and repaired
later, instead of being discarded at parse time.

### Link type

`ExtractedLink.LinkType` — `INTERNAL_WIKI` (`[[Target]]` or `[[Target|alias]]`) and `INTERNAL_SLUG`
(a normal Markdown link whose href is a platform path). Both produce the same kind of edge; the
distinction records how the author wrote it.

### Link resolution state

`LinkResolutionState` — `RESOLVED` or `UNRESOLVED`.

`UNRESOLVED` is a normal, expected state, not an error: it means the target does not exist *yet*.
The link renders as visibly broken and starts working by itself when the target is published.

### Authored vs inferred

The distinction the graph UI draws as solid vs dashed lines.

**Authored** — a person wrote this link. Authoritative.

**Inferred** — the platform proposed it (`SuggestedRelationship`), by `RelationshipKind`:
`SHARED_TOPIC`, `SHARED_TAG`, `SEMANTIC_SIMILARITY`.

An inference must never be presented as an author's assertion.

### Backlink

An inbound authored link: the articles that link *to* this one. Not stored separately — it is the
edge read in the other direction.

### Neighbourhood

`GraphNeighbourhood` — a **bounded** subgraph around one article: nodes, edges, a depth, and a
`truncated` flag saying the picture is partial.

The word is doing real work. The graph API never returns "the graph"; it returns a neighbourhood,
because an unbounded traversal is the failure mode this design exists to avoid. The one whole-graph
endpoint returns a capped *sample* and says so.

### Distance

Hops from the article a neighbourhood was built around. The focused article is `0`.

---

## Ingestion

### Ingestion run

`IngestionRun` — one execution of the pipeline over one repository at one revision. The unit an
author looks at when asking "did my commit arrive".

### Ingestion state

`IngestionState` — `RUNNING`, `SUCCEEDED`, `PARTIALLY_SUCCEEDED`, `FAILED`.

`PARTIALLY_SUCCEEDED` must not be collapsed into failure. One malformed file among two hundred is a
partial success and an author needs to see the difference at a glance.

### Document outcome

`DocumentOutcome` — `SUCCEEDED`, `FAILED`, `SKIPPED`, per file.

**`SKIPPED` is healthy.** It means the content was already current. Styling it as a problem trains
authors to ignore the one screen that tells them the truth.

### Trigger

`IngestionRun.Trigger` — `WEBHOOK`, `MANUAL`, `SCHEDULED`. How a run started.

### Diagnostic

`Diagnostic` / `IngestionEvent` — a structured finding from a run: `severity`, `code`, `sourcePath`,
`message`.

Codes are stable identifiers a UI can branch on; messages are for humans. Current codes:
`UNRESOLVED_LINK`, `MISSING_TITLE`, `RAW_HTML_DROPPED`, `PARSE_FAILED`, `PERSIST_FAILED`,
`UNCHANGED`, `PROVIDER_ERROR`.

`IngestionSeverity` — `INFO`, `WARNING`, `ERROR`.

### Ingestion context

`IngestionContext` — the mutable state one document carries through the handler chain. Handlers read
from it and write to it; nothing else is shared between them.

### Handler / chain

The ingestion pipeline is a Chain of Responsibility. Each **handler** is single-purpose and ordered
by an explicit constant:

```
validate → parse → metadata → persist → graph → search → embedding → read model → cache
```

"Handler" always means a link in this chain. Nothing else in the codebase is called one.

### Upsert

`ArticleUpsertCommand` / `ArticleUpsertResult` — create-or-update as one operation, because
ingestion cannot know which it is doing and must not care. Re-ingesting an unchanged document is a
no-op, which is what makes the pipeline idempotent.

---

## Source integration

### Source vs repository vs provider

Precise, and often confused:

- **Provider** — the vendor. `SourceType`: `GITHUB`, `GITLAB`, `AZURE_DEVOPS`.
- **Repository** — a configured connection to one remote repo. What Studio lists.
- **Source** — the module and the general concept of "somewhere content comes from".

`RepositoryDescriptor` is the provider-neutral description an adapter is handed;
`SourceCoordinate` locates one file within it.

### Owner author

The author every article in a repository is attributed to unless a file's frontmatter says
otherwise. Follows from one repository per author — the repository *is* the identity.

### Factory / strategy / adapter

The three-layer boundary for provider integration, and the layer names are enforced by convention:

- **Factory** picks the strategy for a `SourceType`.
- **Strategy** expresses provider-neutral behaviour (`GitHubSourceIntegrationStrategyImpl`).
- **Adapter** owns provider-specific API models and quirks.

Provider DTOs never cross out of the adapter. Adding a provider means a new strategy and adapter,
never an edit to ingestion.

### Delegate

`SearchDelegate`, `DeliveryDelegate` — translates between an HTTP contract and a domain service.
Controllers hold no logic; delegates hold no persistence.

### Webhook delivery

`PushNotification` and its `Status` — `ACCEPTED`, `IGNORED`, `REJECTED`.

`IGNORED` means valid but uninteresting, such as a push to a branch that is not the configured one.
`REJECTED` means it failed verification. Recorded separately so a misconfigured webhook is
distinguishable from a quiet one.

---

## Search

### The one-sentence rule

> **The SLM interprets. Search retrieves.**

Everything below follows from it. The model produces exactly one thing, a validated `SearchIntent`.
It never writes SQL, never touches a repository, and is never the ranking engine.

### Search intent

`SearchIntent` — the structured, schema-validated interpretation of a query: intent type, author,
topics, tags, date range.

Model output is treated as untrusted and validated before use. `QueryIntentType`: `ARTICLE_SEARCH`,
`AUTHOR_SEARCH`, `TOPIC_SEARCH`, `UNKNOWN`.

### Query understanding

The step that turns query text into a `SearchIntent`. May be the SLM, may be deterministic parsing.

The **analyzer** decides *which* interpreter runs, not whether one runs at all: a query that does not
warrant an inference call is still parsed deterministically, so explicit field syntax works either
way.

### Plan / retriever / candidate / ranker

The retrieval pipeline, in order:

- `SearchPlan` — which retrievers to run, with which filters and mode.
- **Retriever** — one retrieval strategy. `FullTextRetriever`, `VectorRetriever`, `MetadataRetriever`.
- `RetrievalCandidate` — one proposed result with the retriever's own score and its
  `Source`: `FULL_TEXT`, `VECTOR`, `METADATA`.
- **Ranker** (`ResultRanker`) — merges candidates into a final order. Deterministic Java. The only
  component that decides what is best.

### Search mode

`SearchMode` — `LEXICAL`, `SEMANTIC`, `HYBRID`. Which retrieval actually ran, reported on the
response so an unexpected result set can be explained without reading the logs.

### Lexical vs semantic

**Lexical** — PostgreSQL full text. Matches words. `tsvector`, `websearch_to_tsquery`, `ts_rank_cd`.

**Semantic** — pgvector cosine distance over embeddings. Matches meaning.

They fail in opposite directions, which is why hybrid exists.

### Explanation / matched by

`ScoreExplanation` — the weighted contributions to a hit: `lexical`, `semantic`, `authorMatch`,
`topicMatch`, `tagMatch`, `recency`, plus `matchedBy`.

Exists to be shown to the reader **as words** — "matched title and tags" — never as a raw score. A
number tells a human nothing.

### Interpretation

`SearchResponse.Interpretation` — how the query was read, returned alongside results.

`authorResolved` is the field that turns an empty result set from a dead end into an explanation: an
author was named and matched nobody.

### Suggestion

`SearchSuggestion` — a typeahead row. **Not a shortened search.** Full-text matching works on stemmed
whole words, so it returns nothing for a word still being typed — the entire window a typeahead is
useful. Suggestions are prefix and substring matches on titles and author names, and are matched
rather than ranked.

---

## Delivery

### Route

`Route` / `RouteTarget` — a materialized mapping from a public URL path to what lives there.
`TargetType`: `ARTICLE`, `AUTHOR`, `TOPIC`, `TAG`.

Materialized so a lookup is one indexed read, rather than guessing an entity type by trying each
repository in turn.

### Navigation model

`NavigationModel` — the precomputed site navigation. Reflects what is actually published, so nobody
maintains a menu by hand.

### Assembler

`ArticleReadModelAssembler` — builds a read model from canonical entities. Not a service and not a
handler; assembling is all it does.

### Invalidation

Removing a cache entry that is now wrong. **Targeted** invalidation names what changed; clearing the
whole cache on every content change is explicitly not the design.

Note the pairing: the cache is invalidated, the read model is *rematerialized*. They are different
operations and the words are not interchangeable.

### View

`analytics.article_view_counts` — how many times an article was read, bucketed by day.

Anonymous by construction: no IP, user agent, session or reader identity. The table cannot answer
who read what, only how often something was read.

**Trending** ranks by views in a window and is a property of the *traffic*. **Most connected** ranks
by distinct linked neighbours and is a property of the *writing*. An article can be central and
unread; they are not two views of the same thing.

---

## Jobs

### Job / job queue

`JobQueue` — the abstraction for deferred work. Currently in-process on a bounded executor; the
abstraction exists so a durable implementation can replace it without touching callers.

`JobState` — `PENDING`, `RUNNING`, `RETRYING`, `SUCCEEDED`, `FAILED`, `DEDUPLICATED`.

`DEDUPLICATED` means an equivalent job was already queued. Reported rather than silently dropped, so
"nothing happened" is distinguishable from "nothing needed to happen".

### Idempotent

Running it twice produces the same result as running it once. Required of every job, and the
property that makes "sync again" the correct way to retry a failed ingestion.

---

## Naming conventions

### Service, and its implementation

Every service is an interface plus one implementation. Callers depend on the interface.

| Kind | Pattern | Example |
|---|---|---|
| Strategy | `<Strategy><Service>Impl` | `GitHubSourceIntegrationStrategyImpl` |
| Everything else | `Default<Service>Impl` | `DefaultArticleServiceImpl` |

This applies to `@Service` beans. Pattern collaborators — handlers, retrievers, the ranker, the
parser, assemblers — stay concrete `@Component` classes unless more than one implementation genuinely
exists. A `Default…Impl` with no second implementation and no interface worth having is noise.

### Package layout

`controller` · `delegate` · `service` · `repository` · `model` (`entity`, `dto`, `request`,
`response`)

Adapters and factories get sibling packages at the module's top level rather than nesting under
`service`, because they are boundaries rather than application logic.

### Module

A Maven module, a package root, and usually a database schema — the same boundary expressed three
ways. `content`, `author`, `source`, `ingestion`, `graph`, `search`, `ai`, `delivery`, `jobs`, plus
`common` and `app`.

"Modular monolith" means these are real modules with real dependency edges, deployed as one process.
Not microservices, and not packages pretending to be modules.
