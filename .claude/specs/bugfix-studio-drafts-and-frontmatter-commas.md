# Bug fix — studio drafts, front-matter commas, caller errors answered as 500

Status: **Implemented** on `bugfix/studio-drafts-and-frontmatter-commas` (2026-11-26). Not merged.
Source: frontend backlog `knowledge-graph-fe/docs/backend-gaps.md` (BG-21, BG-18, BG-12) plus two
defects observed while building and verifying the studio (unknown sort property → 500, unknown
path → 500).

## 1. Defects

### D1 — Studio article list hides drafts and archived articles (BG-21)

- **Symptom.** `GET /api/studio/articles` returns only `PUBLISHED` articles, for admins and authors.
  Unpublishing an article makes it vanish from the studio; it can only be re-published by someone who
  knows its slug and calls `POST /api/studio/articles/{slug}/publish` directly. Archived articles are
  equally unreachable.
- **Reproduce.** Publish → `POST /api/studio/articles/x/unpublish` → `GET /api/studio/articles`:
  `x` is absent and `totalElements` excludes it, while `GET /api/studio/articles/x` still returns it
  as `DRAFT`.
- **Root cause.** `ArticleDelegate.listVisible` reuses the *public reader* queries
  (`ArticleService.findPublished` / `findPublishedByAuthor`), which filter on `PUBLISHED` and order by
  `publishedAt`. The editorial list has a different contract — every article the caller may edit,
  in any state — but no query for it exists. The OpenAPI summary ("Published articles this account may
  edit") documents the bug rather than the intent: every other studio article operation is explicitly
  "whatever its publication state".
- **Impact.** Editorial dead end in the studio (frontend F07); unpublish/archive are effectively
  one-way from the UI.

### D2 — Front-matter scalar values are cut at the first comma (BG-18)

- **Symptom.** `summary: Combining lexical and semantic retrieval, and why the scores must be
  normalized…` is stored as "Combining lexical and semantic retrieval". The same happens to `title`,
  `description`, `author` and `slug`: `title: Raft, explained` becomes "Raft".
- **Root cause.** `Frontmatter.first(key)` is implemented as `all(key).first`, and `all` splits every
  value on commas to support the single-line list form (`tags: postgres, indexing`). List semantics
  leak into scalar keys.
- **Impact.** Wrong summaries and titles across the site, search snippets and graph labels; silently
  — no diagnostic is raised.

### D3 — Unknown sort property returns 500 (new, found during frontend work)

- **Symptom.** `GET /api/studio/articles?sort=nonexistent,desc` (and any endpoint taking `Pageable`)
  answers `500 INTERNAL_ERROR` and logs an ERROR with a stack trace.
- **Root cause.** Spring Data throws `org.springframework.data.core.PropertyReferenceException` for an
  unknown sort property; `GlobalExceptionHandler` has no mapping for it, so it falls through to the
  catch-all `Exception` handler.
- **Impact.** A client mistake is reported as a server failure (wrong status, ERROR-level noise,
  alerting on caller input).

### D4 — `GET /api/search` documents `size` with default `0` (BG-12) — documentation only

- **Finding.** Not a runtime defect: `size=0` (or absent) is a sentinel the service maps to
  `knowledge.search.default-page-size` (20), clamped to `max-page-size` (50). The published contract
  shows `default: 0` with no explanation, which reads as "returns nothing".
- **Fix.** Document the parameter (meaning of 0, default and cap). Behaviour unchanged.

### D5 — Unknown path returns 500 (new, found during verification)

- **Symptom.** `GET /api/studio/runs` (no such route) answers `500 INTERNAL_ERROR` with an ERROR log
  and stack trace.
- **Root cause.** Spring MVC hands unmatched paths to the static-resource handler, which raises
  `NoResourceFoundException`; it has no mapping and reaches the catch-all, like D3.
- **Impact.** Typos, crawlers and scanners produce ERROR-level noise and 500s instead of 404s.

## 2. Fix design

### D1
- `ArticleService` gains editorial queries alongside the reader ones (reader behaviour untouched —
  graph, delivery and ingestion keep using `findPublished*`):
  - `Page<Article> findEditable(Pageable)` — every article, any state (administrators).
  - `Page<Article> findEditableByAuthor(UUID authorId, Pageable)` — one byline's articles, any state.
- `ArticleRepository` gains `findByAuthorId(UUID, Pageable)`; the admin case uses `findAll(Pageable)`.
  Scoping stays in the query (not filtered after paging), preserving honest page sizes and totals.
- Default order when the caller gives no sort: `updatedAt` descending — the editorial question is
  "what changed recently", not "what was published recently" (drafts have no `publishedAt`). A
  caller-supplied sort is respected.
- `ArticleDelegate.listVisible` switches to the editorial queries; ownership rules are unchanged
  (admin → all; author → own byline; no byline → empty page).
- Operation summary corrected to "Articles this account may edit, in any publication state".

### D2
- `Frontmatter.first(key)` returns the first non-blank raw value, trimmed, **without** comma
  splitting. `all(key)` keeps both list forms (YAML list and comma-separated single line).
- Callers verified: every `first` call reads a scalar key (`title`, `summary`, `description`,
  `author`, `author_email`, `slug`, flags, dates); every list key (`tags`, `topics`) uses `all`.
- Behaviour change to note: `author: Alice, Bob` now yields the author name "Alice, Bob" instead of
  silently picking "Alice". The platform has one author per article; a comma in a name is preserved.
- **Repairing existing data.** A plain resync would *not* repair stored values: the content hash
  covers the source bytes, which have not changed, so ingestion skips the document as unchanged.
  Recording a new revision is not an option either (`article_revisions` is `UNIQUE (article_id,
  content_hash)`, and the content did not change). The fix therefore adds a *derived-metadata
  refresh* on the unchanged-hash branch of `ArticleService.upsert`:
  - `Article.refreshDerivedMetadata(title, summary)` updates title and summary when they differ from
    the freshly parsed values; revision number and hash stay untouched.
  - When it reports a change, taxonomy is re-applied (`applyTaxonomy`, which also initialises the
    lazy collections the later `update-graph` step reads) and the result is flagged
    `derivedMetadataRefreshed`, so `requiresRematerialization()` is true and graph, search and read
    model are rebuilt.
  - Publication state is deliberately **not** re-applied: a studio unpublish of an unchanged
    document must survive a resync.
  - Logged at INFO ("re-derived at unchanged hash"), once per repaired article.
  Operational step after deploy: `POST /api/studio/sources/{id}/sync` for each source.

### D3
- `GlobalExceptionHandler` maps `PropertyReferenceException` to `400 INVALID_REQUEST` with the
  property name ("Unknown sort property 'x'"), logged at WARN (caller error), no stack trace in the
  response.

### D5
- `GlobalExceptionHandler` maps `NoResourceFoundException` to `404 NOT_FOUND` ("No such endpoint"),
  logged at DEBUG — unknown paths are routine and must not alert.

### D4
- `@Parameter(description = …)` on `size` (and `page`) in `SearchController`.

## 3. Out of scope
- Server-side publication-state filter on the studio list (BG-14) — a feature, not this fix.
- Regenerating `apim/api-spec.yaml` — done after merge from the running app (tasklog procedure).
- Backfilling truncated values in existing databases beyond the documented resync.
- **Follow-up (pre-existing, not fixed here):** a run that fails *after* `persist-content` leaves the
  article committed but graph/search/read model stale, and later resyncs skip it as unchanged, so
  nothing heals it. Observed during verification (an intermediate build of this fix failed in
  `update-graph`). Candidate fixes: mark the source/article as needing rematerialisation on handler
  failure, or add an admin "force rematerialise" action. Recorded for the backlog.

## 4. Acceptance criteria
- AC-1 Admin `GET /api/studio/articles` includes DRAFT and ARCHIVED articles; `totalElements` counts
  them.
- AC-2 An author sees only their own byline's articles, in every state; an account without a byline
  gets an empty page.
- AC-3 Without a `sort` parameter the list is ordered by `updatedAt` descending; with one, it is
  honoured.
- AC-4 `Frontmatter.first("summary")` of `"a, b, c"` is `"a, b, c"`; `all("tags")` of
  `"postgres, indexing"` is `["postgres", "indexing"]`; YAML list form still works for both.
- AC-5 A Markdown document with `title: Raft, explained` and a comma-containing summary keeps both
  intact through `CanonicalDocumentFactory`.
- AC-6 `?sort=nonexistent,desc` → `400` with code `INVALID_REQUEST`; no ERROR log.
- AC-7 Public reader endpoints are unchanged (still `PUBLISHED` only).
- AC-8 A request to an unmapped path → `404` with code `NOT_FOUND`; no ERROR log.
- AC-9 After deploy, a resync of an unchanged document whose stored title/summary were truncated
  corrects them and rebuilds the read model, without a new revision and without changing its
  publication state.

## 5. Verification
- Unit: `FrontmatterTest` (new), `CanonicalDocumentFactoryTest` (AC-5), `ArticleDelegateTest`
  (new, Mockito: query selection per principal), `DefaultArticleServiceImplEditorialTest` (new:
  default/requested sort, refresh at unchanged hash, publication state kept, taxonomy re-applied),
  `ArticleTest` (refresh without new revision), `GlobalExceptionHandlerTest` (new: AC-6, AC-8).
- Full build `./mvnw package`: 320 tests, 0 failures, 74 skipped (Testcontainers; no Docker here).
- Integration (Testcontainers; skipped locally without a container runtime): existing
  `OwnershipIntegrationTest` covers studio scoping end to end.
- Manual, local app + three sample GitHub repositories (2026-11-26):
  - AC-9: with stored summaries in the pre-fix truncated state, resync logged 5 × "re-derived at
    unchanged hash", all runs succeeded; `/api/articles/{slug}` now serves full summaries (e.g.
    "Cosine distance, HNSW versus IVFFlat, and why the index has to match the operator.").
  - AC-1/AC-3: after unpublishing `kubernetes-networking`, admin list shows it as `DRAFT` first
    (latest `updatedAt`), `totalElements` 6; AC-7: public reader returns 404 for it. Re-published.
  - AC-2 (no-byline case): local `author` account has no `authorId` → empty page.
    Author-with-byline is covered by unit test only.
  - AC-6: `?sort=nonexistent,desc` → 400 `INVALID_REQUEST`, one WARN, no ERROR.
  - AC-8: `/api/studio/runs` → 404 `NOT_FOUND`.
  - D4: `/api/search?q=postgres` without `size` → `size` 20.

## 6. Rollback
Code-only change (no migration). Revert the branch's commits. Already-repaired titles/summaries stay
correct until a document changes and is re-parsed by the old code.
