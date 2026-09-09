package com.knowledge.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.author.service.AuthorService;
import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.model.entity.PublicationState;
import com.knowledge.platform.content.service.ArticleService;
import com.knowledge.platform.delivery.model.dto.ArticleReadModel;
import com.knowledge.platform.delivery.model.dto.ArticleReference;
import com.knowledge.platform.delivery.model.dto.RouteTarget;
import com.knowledge.platform.delivery.service.ReadModelService;
import com.knowledge.platform.delivery.service.RouteResolutionService;
import com.knowledge.platform.graph.model.entity.ArticleLink;
import com.knowledge.platform.graph.service.LinkResolutionService;
import com.knowledge.platform.ingestion.model.entity.IngestionEvent;
import com.knowledge.platform.ingestion.model.entity.IngestionRun;
import com.knowledge.platform.ingestion.model.entity.IngestionState;
import com.knowledge.platform.ingestion.service.IngestionRunRecorder;
import com.knowledge.platform.ingestion.service.IngestionService;
import com.knowledge.platform.source.integration.adapter.github.GitHubSourceAdapterImpl;
import com.knowledge.platform.source.model.dto.RemoteEntry;
import com.knowledge.platform.source.model.dto.SourceFile;
import com.knowledge.platform.source.model.dto.SourceSyncRequestedEvent;
import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.model.entity.SourceType;
import com.knowledge.platform.source.service.SourceService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The materialization pipeline, end to end, against a real database.
 *
 * <p>Only the provider itself is stubbed. {@link GitHubSourceAdapterImpl} is replaced, and everything
 * above it -- strategy, source service, the whole handler chain, content, graph, search and delivery
 * -- runs for real against PostgreSQL. That boundary is deliberate: it is exactly the seam the
 * architecture defines, so the test exercises the system as built rather than a rehearsal of it.
 */
@EnabledIf(value = "com.knowledge.platform.AbstractPostgresIntegrationTest#containerRuntimeAvailable",
        disabledReason = "No container runtime; set DOCKER_HOST for rootless Podman")
class IngestionPipelineIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String NETWORKING_PATH = "docs/kubernetes-networking.md";
    private static final String DEPLOYMENT_PATH = "docs/deployment.md";

    private static final String NETWORKING_MARKDOWN = """
            ---
            title: Kubernetes Networking
            author: Harsh
            tags: kubernetes, networking, cni
            topics: Kubernetes
            ---

            Pods get routable addresses, and the CNI plugin is what makes that true.

            ## The pod network

            Every pod gets its own address. See [[Deployment]] for how that is rolled out.

            ```yaml
            apiVersion: v1
            kind: Pod
            ```
            """;

    private static final String DEPLOYMENT_MARKDOWN = """
            ---
            title: Deployment
            author: Harsh
            tags: kubernetes, deployment
            topics: Kubernetes
            ---

            Rolling updates replace pods gradually, back to [[Kubernetes Networking]].
            """;

    @MockitoBean
    private GitHubSourceAdapterImpl gitHubAdapter;

    @Autowired
    private SourceService sourceService;

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private IngestionRunRecorder runRecorder;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private AuthorService authorService;

    @Autowired
    private LinkResolutionService linkResolutionService;

    @Autowired
    private ReadModelService readModelService;

    @Autowired
    private RouteResolutionService routeResolutionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Map<String, String> repositoryContents = new HashMap<>();
    private SourceRepository repository;

    @BeforeEach
    void setUp() {
        // Each test starts from an empty corpus. Truncating rather than rolling back a transaction:
        // the pipeline commits across several transactional units, which a test-managed rollback
        // would not contain.
        jdbcTemplate.execute("""
                truncate table read_model.article_read_models, read_model.routes,
                               search.article_embeddings, search.article_search_documents,
                               graph.suggested_relationships, graph.article_links,
                               content.article_topics, content.article_tags,
                               content.article_revisions, content.articles,
                               content.topics, content.tags,
                               ingestion.events, ingestion.runs,
                               source.webhook_deliveries, source.repositories,
                               author.authors
                restart identity cascade
                """);

        repositoryContents.clear();
        repository = sourceService.connect(SourceType.GITHUB, "Handbook", "acme", "handbook",
                null, "main", "docs", null, "token", "secret", null, null);

        stubProvider();
    }

    /** Serves whatever {@link #repositoryContents} currently holds, as the real adapter would. */
    private void stubProvider() {
        when(gitHubAdapter.providerType()).thenReturn(SourceType.GITHUB);
        when(gitHubAdapter.resolveHeadRevision(any(), anyString())).thenReturn(Optional.of("rev-1"));
        when(gitHubAdapter.listTree(any(), anyString(), anyString())).thenAnswer(invocation ->
                repositoryContents.keySet().stream()
                        .map(path -> new RemoteEntry(path, "blob-" + path.hashCode(), -1))
                        .toList());
        when(gitHubAdapter.readFile(any(), anyString(), anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(1);
            String revision = invocation.getArgument(2);
            String content = repositoryContents.get(path);
            return content == null
                    ? Optional.empty()
                    : Optional.of(new SourceFile(path, content, revision, "blob-" + path.hashCode()));
        });
    }

    private IngestionRun ingest(String revision) {
        return ingestionService.ingest(
                SourceSyncRequestedEvent.fullResync(repository.getId(), revision,
                        SourceSyncRequestedEvent.Trigger.MANUAL));
    }

    @Nested
    @EnabledIf(value = "com.knowledge.platform.AbstractPostgresIntegrationTest#containerRuntimeAvailable",
            disabledReason = "No container runtime; set DOCKER_HOST for rootless Podman")
    @DisplayName("materializing a document")
    class Materialization {

        @Test
        void producesCanonicalContentWithItsDerivedMetadata() {
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);

            IngestionRun run = ingest("rev-1");

            assertThat(run.getState()).isEqualTo(IngestionState.SUCCEEDED);
            assertThat(run.getDocumentsSucceeded()).isEqualTo(1);

            Article article = articleService.requireBySlug(Slug.of("kubernetes-networking"));
            assertThat(article.getTitle()).isEqualTo("Kubernetes Networking");
            assertThat(article.getSummary())
                    .isEqualTo("Pods get routable addresses, and the CNI plugin is what makes that true.");
            assertThat(article.getPublicationState()).isEqualTo(PublicationState.PUBLISHED);
            assertThat(article.getSourcePath()).isEqualTo(NETWORKING_PATH);
            assertThat(article.getWordCount()).isPositive();
            assertThat(article.getTags()).extracting(tag -> tag.getSlug())
                    .containsExactlyInAnyOrder("kubernetes", "networking", "cni");
            assertThat(article.getTopics()).extracting(topic -> topic.getSlug())
                    .containsExactly("kubernetes");
        }

        @Test
        @DisplayName("the author named in frontmatter is created on first sight")
        void createsTheAuthorFromFrontmatter() {
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);
            ingest("rev-1");

            Integer authors = jdbcTemplate.queryForObject(
                    "select count(*) from author.authors where slug = 'harsh'", Integer.class);
            assertThat(authors).isEqualTo(1);
        }

        @Test
        void storesARevisionOfTheCanonicalMarkdown() {
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);
            ingest("rev-1");

            Integer revisions = jdbcTemplate.queryForObject(
                    "select count(*) from content.article_revisions", Integer.class);
            assertThat(revisions).isEqualTo(1);
        }

        @Test
        void writesTheLexicalSearchDocument() {
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);
            ingest("rev-1");

            Integer matches = jdbcTemplate.queryForObject("""
                    select count(*) from search.article_search_documents
                     where search_vector @@ websearch_to_tsquery('english', 'pod network')
                    """, Integer.class);
            assertThat(matches).isEqualTo(1);
        }

        @Test
        @DisplayName("code content is searchable: an identifier is often what a reader looks for")
        void indexesCodeBlockContent() {
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);
            ingest("rev-1");

            Integer matches = jdbcTemplate.queryForObject("""
                    select count(*) from search.article_search_documents
                     where search_vector @@ websearch_to_tsquery('english', 'apiVersion')
                    """, Integer.class);
            assertThat(matches).isEqualTo(1);
        }

        @Test
        @DisplayName("the author's name is indexed, so naming them in free text still matches")
        void indexesAuthorAndTaxonomyAsMetadata() {
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);
            ingest("rev-1");

            String metadata = jdbcTemplate.queryForObject(
                    "select metadata_text from search.article_search_documents", String.class);
            assertThat(metadata).contains("Harsh").contains("Kubernetes");
        }

        @Test
        void registersThePublicRoute() {
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);
            ingest("rev-1");

            RouteTarget target = routeResolutionService.resolve("/articles/kubernetes-networking");
            assertThat(target.targetType()).isEqualTo(RouteTarget.TargetType.ARTICLE);
            assertThat(target.targetSlug()).isEqualTo("kubernetes-networking");
        }

        @Test
        @DisplayName("the read model is materialized during ingestion, not on the first reader's request")
        void materializesTheReadModel() {
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);
            ingest("rev-1");

            Integer stored = jdbcTemplate.queryForObject(
                    "select count(*) from read_model.article_read_models", Integer.class);
            assertThat(stored).isEqualTo(1);

            ArticleReadModel model = readModelService.articleBySlug(Slug.of("kubernetes-networking"));
            assertThat(model.title()).isEqualTo("Kubernetes Networking");
            assertThat(model.author().displayName()).isEqualTo("Harsh");
            assertThat(model.body()).isNotEmpty();
            assertThat(model.outline()).extracting(heading -> heading.anchorId())
                    .contains("the-pod-network");
            assertThat(model.breadcrumbs()).extracting(crumb -> crumb.label())
                    .containsExactly("Home", "Kubernetes", "Kubernetes Networking");
        }
    }

    @Nested
    @EnabledIf(value = "com.knowledge.platform.AbstractPostgresIntegrationTest#containerRuntimeAvailable",
            disabledReason = "No container runtime; set DOCKER_HOST for rootless Podman")
    @DisplayName("internal links")
    class Links {

        @Test
        @DisplayName("a reference whose target does not exist yet is kept, and reported")
        void recordsDanglingReferencesAsDiagnostics() {
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);

            IngestionRun run = ingest("rev-1");

            Article article = articleService.requireBySlug(Slug.of("kubernetes-networking"));
            assertThat(linkResolutionService.outboundLinks(article.getId()))
                    .singleElement()
                    .satisfies(link -> {
                        assertThat(link.getTargetSlug()).isEqualTo("deployment");
                        assertThat(link.isResolved()).isFalse();
                    });

            assertThat(runRecorder.diagnosticsFor(run.getId()))
                    .anySatisfy(event -> {
                        assertThat(event.getCode()).isEqualTo(IngestionEvent.CODE_UNRESOLVED_LINK);
                        assertThat(event.getMessage()).contains("Deployment");
                    });
        }

        @Test
        @DisplayName("publishing the target repairs every reference that was waiting for it")
        void resolvesDanglingReferencesWhenTheTargetAppears() {
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);
            ingest("rev-1");

            // The referring article is not re-ingested; the graph repairs itself from the other side.
            repositoryContents.put(DEPLOYMENT_PATH, DEPLOYMENT_MARKDOWN);
            ingest("rev-2");

            Article networking = articleService.requireBySlug(Slug.of("kubernetes-networking"));
            Article deployment = articleService.requireBySlug(Slug.of("deployment"));

            assertThat(linkResolutionService.outboundLinks(networking.getId()))
                    .singleElement()
                    .satisfies(link -> {
                        assertThat(link.isResolved()).isTrue();
                        assertThat(link.getTargetArticleId()).isEqualTo(deployment.getId());
                    });
        }

        @Test
        void recordsBacklinksInBothDirections() {
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);
            repositoryContents.put(DEPLOYMENT_PATH, DEPLOYMENT_MARKDOWN);
            ingest("rev-1");

            Article networking = articleService.requireBySlug(Slug.of("kubernetes-networking"));

            assertThat(linkResolutionService.backlinks(networking.getId()))
                    .extracting(ArticleLink::getTargetSlug)
                    .containsExactly("kubernetes-networking");
        }

        @Test
        @DisplayName("the read model reports link resolution, so the page can render a missing link")
        void surfacesUnresolvedReferencesInTheReadModel() {
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);
            ingest("rev-1");

            ArticleReadModel model = readModelService.articleBySlug(Slug.of("kubernetes-networking"));

            assertThat(model.unresolvedReferences()).containsExactly("Deployment");
        }
    }

    @Nested
    @EnabledIf(value = "com.knowledge.platform.AbstractPostgresIntegrationTest#containerRuntimeAvailable",
            disabledReason = "No container runtime; set DOCKER_HOST for rootless Podman")
    @DisplayName("one repository per author")
    class PerAuthorRepositories {

        /** Connects a second repository owned by a different author. */
        private SourceRepository connectRepositoryFor(String authorName, String email) {
            SourceRepository connected = sourceService.connect(
                    SourceType.GITHUB, authorName + " notes", slugish(authorName), "notes",
                    null, "main", "docs", null, "token", "secret", authorName, email);
            stubProvider();
            return connected;
        }

        private String slugish(String name) {
            return name.toLowerCase().replace(' ', '-');
        }

        @Test
        @DisplayName("a repository declares its author, so its files need no author frontmatter")
        void attributesArticlesToTheRepositoryOwner() {
            SourceRepository anitas = connectRepositoryFor("Anita Rao", "anita@example.com");
            repositoryContents.put("docs/indexes.md", """
                    ---
                    title: PostgreSQL Indexes
                    ---

                    B-tree indexes accelerate range predicates on ordered columns.
                    """);

            ingestionService.ingest(SourceSyncRequestedEvent.fullResync(
                    anitas.getId(), "rev-1", SourceSyncRequestedEvent.Trigger.MANUAL));

            Article article = articleService.requireBySlug(Slug.of("postgresql-indexes"));
            assertThat(authorService.requireById(article.getAuthorId()).getDisplayName())
                    .isEqualTo("Anita Rao");
        }

        @Test
        @DisplayName("frontmatter still wins, so a guest post in someone's repository is attributed right")
        void frontmatterOverridesTheRepositoryOwner() {
            SourceRepository anitas = connectRepositoryFor("Anita Rao", "anita@example.com");
            repositoryContents.put("docs/guest.md", """
                    ---
                    title: A Guest Post
                    author: Meera Iyer
                    ---

                    Written by somebody else entirely.
                    """);

            ingestionService.ingest(SourceSyncRequestedEvent.fullResync(
                    anitas.getId(), "rev-1", SourceSyncRequestedEvent.Trigger.MANUAL));

            Article article = articleService.requireBySlug(Slug.of("a-guest-post"));
            assertThat(authorService.requireById(article.getAuthorId()).getDisplayName())
                    .isEqualTo("Meera Iyer");
        }

        @Test
        @DisplayName("a later repository linking in updates the target's backlinks")
        void rebuildsTheReadModelOfAnArticleThatGainsABacklink() {
            // The target's own content never changes here — only who points at it. Its persisted
            // read model is only considered stale when its content hash changes, so without an
            // explicit rebuild it would serve an incomplete backlink list forever. With one
            // repository per author this is the normal case, not an edge case: cross-repository
            // links arrive whenever the other author's repository happens to sync.
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);
            ingest("rev-1");
            assertThat(readModelService.articleBySlug(Slug.of("kubernetes-networking")).backlinks())
                    .isEmpty();

            SourceRepository meeras = connectRepositoryFor("Meera Iyer", "meera@example.com");
            repositoryContents.clear();
            repositoryContents.put("docs/ops.md", """
                    ---
                    title: Operations Notes
                    ---

                    Builds on [[Kubernetes Networking]] from another author's repository.
                    """);
            ingestionService.ingest(SourceSyncRequestedEvent.fullResync(
                    meeras.getId(), "rev-2", SourceSyncRequestedEvent.Trigger.MANUAL));

            assertThat(readModelService.articleBySlug(Slug.of("kubernetes-networking")).backlinks())
                    .extracting(ArticleReference::title)
                    .containsExactly("Operations Notes");
        }

        @Test
        @DisplayName("a link across repositories resolves once the other repository is ingested")
        void repairsCrossRepositoryLinksRegardlessOfIngestionOrder() {
            SourceRepository anitas = connectRepositoryFor("Anita Rao", "anita@example.com");
            // Anita links to an article living in someone else's repository, which does not exist
            // yet. With independent per-author repositories the ingestion order can never be
            // guaranteed, so this has to become a repairable unresolved edge rather than be lost.
            repositoryContents.put("docs/vectors.md", """
                    ---
                    title: Vector Search
                    ---

                    Complements [[Kubernetes Networking]], written by someone else.
                    """);
            ingestionService.ingest(SourceSyncRequestedEvent.fullResync(
                    anitas.getId(), "rev-1", SourceSyncRequestedEvent.Trigger.MANUAL));

            Article vectors = articleService.requireBySlug(Slug.of("vector-search"));
            assertThat(linkResolutionService.outboundLinks(vectors.getId()))
                    .singleElement()
                    .satisfies(link -> assertThat(link.isResolved()).isFalse());

            // The other author's repository arrives second. The referring article is never
            // re-ingested — the graph repairs itself from the other side.
            repositoryContents.clear();
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);
            ingestionService.ingest(SourceSyncRequestedEvent.fullResync(
                    repository.getId(), "rev-2", SourceSyncRequestedEvent.Trigger.MANUAL));

            assertThat(linkResolutionService.outboundLinks(vectors.getId()))
                    .singleElement()
                    .satisfies(link -> assertThat(link.isResolved()).isTrue());
        }
    }

    @Nested
    @EnabledIf(value = "com.knowledge.platform.AbstractPostgresIntegrationTest#containerRuntimeAvailable",
            disabledReason = "No container runtime; set DOCKER_HOST for rootless Podman")
    @DisplayName("idempotency")
    class Idempotency {

        @Test
        @DisplayName("re-ingesting unchanged content skips it rather than rewriting derived state")
        void skipsUnchangedDocuments() {
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);
            ingest("rev-1");

            IngestionRun second = ingest("rev-2");

            assertThat(second.getState()).isEqualTo(IngestionState.SUCCEEDED);
            assertThat(second.getDocumentsSkipped()).isEqualTo(1);
            assertThat(second.getDocumentsSucceeded()).isZero();
        }

        @Test
        void doesNotDuplicateArticlesOrRevisionsOnReplay() {
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);
            ingest("rev-1");
            ingest("rev-1");
            ingest("rev-1");

            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from content.articles", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from content.article_revisions", Integer.class)).isEqualTo(1);
        }

        @Test
        @DisplayName("edited content produces a new revision and keeps the article's identity")
        void appendsARevisionWhenContentChanges() {
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);
            ingest("rev-1");
            UUID originalId = articleService.requireBySlug(Slug.of("kubernetes-networking")).getId();

            repositoryContents.put(NETWORKING_PATH,
                    NETWORKING_MARKDOWN + "\n\nAn additional paragraph.\n");
            ingest("rev-2");

            Article article = articleService.requireBySlug(Slug.of("kubernetes-networking"));
            assertThat(article.getId()).isEqualTo(originalId);
            assertThat(article.getRevisionNumber()).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from content.article_revisions", Integer.class)).isEqualTo(2);
        }

        @Test
        @DisplayName("a retitled file updates its article rather than orphaning it")
        void followsTheSourceCoordinateAcrossARename() {
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);
            ingest("rev-1");
            UUID originalId = articleService.requireBySlug(Slug.of("kubernetes-networking")).getId();

            repositoryContents.put(NETWORKING_PATH,
                    NETWORKING_MARKDOWN.replace("title: Kubernetes Networking",
                            "title: Kubernetes Networking Explained"));
            ingest("rev-2");

            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from content.articles", Integer.class)).isEqualTo(1);
            Article renamed = articleService.requireBySlug(
                    Slug.of("kubernetes-networking-explained"));
            assertThat(renamed.getId()).isEqualTo(originalId);
        }
    }

    @Nested
    @EnabledIf(value = "com.knowledge.platform.AbstractPostgresIntegrationTest#containerRuntimeAvailable",
            disabledReason = "No container runtime; set DOCKER_HOST for rootless Podman")
    @DisplayName("diagnostics")
    class Diagnostics {

        @Test
        @DisplayName("one unusable document does not stop the run")
        void continuesPastAFailedDocument() {
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);
            repositoryContents.put("docs/untitled.md", "Just a paragraph with no title anywhere.");

            IngestionRun run = ingest("rev-1");

            assertThat(run.getState()).isEqualTo(IngestionState.PARTIALLY_SUCCEEDED);
            assertThat(run.getDocumentsSucceeded()).isEqualTo(1);
            assertThat(run.getDocumentsFailed()).isEqualTo(1);
            assertThat(runRecorder.diagnosticsFor(run.getId()))
                    .anySatisfy(event ->
                            assertThat(event.getCode()).isEqualTo(IngestionEvent.CODE_MISSING_TITLE));
        }

        @Test
        @DisplayName("raw HTML is dropped and the author is told, rather than it silently vanishing")
        void reportsDroppedRawHtml() {
            repositoryContents.put("docs/with-html.md", """
                    ---
                    title: With HTML
                    ---

                    Text before.

                    <div onclick="alert('xss')">raw markup</div>

                    Text after.
                    """);

            IngestionRun run = ingest("rev-1");

            assertThat(run.getDocumentsSucceeded()).isEqualTo(1);
            assertThat(runRecorder.diagnosticsFor(run.getId()))
                    .anySatisfy(event ->
                            assertThat(event.getCode()).isEqualTo(IngestionEvent.CODE_RAW_HTML_DROPPED));

            ArticleReadModel model = readModelService.articleBySlug(Slug.of("with-html"));
            assertThat(model.body().toString()).doesNotContain("onclick");
        }

        @Test
        @DisplayName("non-Markdown files are not content and never reach the pipeline")
        void ignoresNonMarkdownFiles() {
            repositoryContents.put(NETWORKING_PATH, NETWORKING_MARKDOWN);
            repositoryContents.put("docs/diagram.png", "not markdown at all");

            IngestionRun run = ingest("rev-1");

            assertThat(run.getDocumentsTotal()).isEqualTo(1);
        }
    }
}
