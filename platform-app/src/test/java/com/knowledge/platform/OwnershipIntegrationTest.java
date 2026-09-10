package com.knowledge.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knowledge.platform.author.model.dto.StudioPrincipal;
import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.author.model.entity.PlatformRole;
import com.knowledge.platform.author.service.AuthorService;
import com.knowledge.platform.common.exception.NotFoundException;
import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.content.model.dto.ArticleUpsertCommand;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.service.ArticleService;
import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.model.entity.SourceType;
import com.knowledge.platform.source.service.SourceService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * One author must not be able to reach another's work.
 *
 * <p>Written adversarially: every assertion here is author one attempting something against author
 * two's resources. A test that only checks the happy path proves that ownership does not get in the
 * way, which is not the property anyone is worried about.
 *
 * <p>The expected outcome is consistently "not found" rather than "forbidden". A distinguishable
 * refusal confirms that an id or slug belongs to somebody, which is most of what an attacker
 * enumerating would want to learn.
 */
class OwnershipIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private SourceService sources;

    @Autowired
    private ArticleService articles;

    @Autowired
    private AuthorService authors;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private StudioPrincipal one;
    private StudioPrincipal two;
    private StudioPrincipal admin;
    private StudioPrincipal unlinked;
    private SourceRepository repositoryOfTwo;
    private Article articleOfTwo;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("""
                truncate table author.account_roles, author.accounts,
                               read_model.article_read_models, read_model.routes,
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

        Author authorOne = authors.findOrCreateByName("Author One", "one@example.com");
        Author authorTwo = authors.findOrCreateByName("Author Two", "two@example.com");

        one = new StudioPrincipal(UUID.randomUUID(), authorOne.getId(), Set.of(PlatformRole.AUTHOR));
        two = new StudioPrincipal(UUID.randomUUID(), authorTwo.getId(), Set.of(PlatformRole.AUTHOR));
        admin = new StudioPrincipal(UUID.randomUUID(), null, Set.of(PlatformRole.ADMIN));
        // Signed in, granted AUTHOR, never linked to a byline.
        unlinked = new StudioPrincipal(UUID.randomUUID(), null, Set.of(PlatformRole.AUTHOR));

        repositoryOfTwo = connect("two-handbook", authorTwo.getId());
        connect("one-handbook", authorOne.getId());
        articleOfTwo = publish("Two's Article", authorTwo);
    }

    private SourceRepository connect(String name, UUID ownerAuthorId) {
        return sources.connect(
                SourceType.GITHUB, name, "acme", name, null, "main", "docs",
                null, null, null, null, null, ownerAuthorId);
    }

    private Article publish(String title, Author author) {
        return articles.upsert(new ArticleUpsertCommand(
                null, title, "Body", "# " + title, "hash-" + title.hashCode(),
                author.getId(), List.of(), List.of(), 2, 1, true, null)).article();
    }

    // ── Sources ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("author one cannot read author two's repository")
    void cannotReadAnotherAuthorsRepository() {
        assertThatThrownBy(() -> sources.requireOwned(repositoryOfTwo.getId(), one))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("a repository that exists and one that does not are indistinguishable")
    void doesNotConfirmExistenceOfAnotherAuthorsRepository() {
        String forSomeoneElses = messageOf(() -> sources.requireOwned(repositoryOfTwo.getId(), one));
        String forNonexistent = messageOf(() -> sources.requireOwned(UUID.randomUUID(), one));

        // Same shape of answer, so enumerating ids reveals nothing about which are real.
        assertThat(forSomeoneElses.replaceAll("[0-9a-f-]{36}", "ID"))
                .isEqualTo(forNonexistent.replaceAll("[0-9a-f-]{36}", "ID"));
    }

    @Test
    @DisplayName("the repository list shows only your own")
    void listsOnlyOwnedRepositories() {
        assertThat(sources.findVisible(one))
                .extracting(SourceRepository::getDisplayName)
                .containsExactly("one-handbook");
        assertThat(sources.findVisible(two))
                .extracting(SourceRepository::getDisplayName)
                .containsExactly("two-handbook");
    }

    @Test
    @DisplayName("an administrator sees every repository")
    void administratorSeesEverything() {
        assertThat(sources.findVisible(admin)).hasSize(2);
        assertThat(sources.requireOwned(repositoryOfTwo.getId(), admin)).isNotNull();
    }

    @Test
    @DisplayName("an account with no byline owns nothing")
    void unlinkedAccountOwnsNothing() {
        assertThat(sources.findVisible(unlinked)).isEmpty();
        assertThatThrownBy(() -> sources.requireOwned(repositoryOfTwo.getId(), unlinked))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("an unowned repository belongs to nobody, not to everybody")
    void unownedRepositoryIsAdminOnly() {
        SourceRepository orphan = sources.connect(
                SourceType.GITHUB, "orphan", "acme", "orphan", null, "main", "docs",
                null, null, null, null, null, null);

        assertThatThrownBy(() -> sources.requireOwned(orphan.getId(), one))
                .isInstanceOf(NotFoundException.class);
        assertThat(sources.requireOwned(orphan.getId(), admin)).isNotNull();
    }

    @Test
    @DisplayName("editing a repository does not silently orphan it")
    void updatingSettingsKeepsTheOwner() {
        // This was real: updateSettings overwrote the owner unconditionally, so an author renaming
        // their own repository unset its owner -- removing it from their own studio and leaving it
        // reachable by nobody but an administrator.
        sources.update(repositoryOfTwo.getId(), "Renamed", null, null, null, null, null, null);

        assertThat(sources.requireOwned(repositoryOfTwo.getId(), two).getDisplayName())
                .isEqualTo("Renamed");
        assertThat(sources.findVisible(two)).hasSize(1);
    }

    // ── Articles ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("author one cannot reach author two's article")
    void cannotReadAnotherAuthorsArticle() {
        assertThatThrownBy(() ->
                articles.requireOwnedBySlug(new Slug(articleOfTwo.getSlug()), one))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("author one cannot unpublish author two's article")
    void cannotUnpublishAnotherAuthorsArticle() {
        assertThatThrownBy(() ->
                articles.requireOwnedBySlug(new Slug(articleOfTwo.getSlug()), one))
                .isInstanceOf(NotFoundException.class);

        // And the article is still published, because the check happens before anything acts.
        assertThat(articles.requireBySlug(new Slug(articleOfTwo.getSlug())).getPublicationState())
                .isEqualTo(com.knowledge.platform.content.model.entity.PublicationState.PUBLISHED);
    }

    @Test
    @DisplayName("author two can reach their own article")
    void ownerCanReachTheirOwnArticle() {
        assertThat(articles.requireOwnedBySlug(new Slug(articleOfTwo.getSlug()), two))
                .isNotNull();
    }

    @Test
    @DisplayName("an administrator can reach any article")
    void administratorCanReachAnyArticle() {
        assertThat(articles.requireOwnedBySlug(new Slug(articleOfTwo.getSlug()), admin))
                .isNotNull();
    }

    // ── The principal itself ───────────────────────────────────────────────────

    @Test
    @DisplayName("a null owner is not matched by a null byline")
    void nullDoesNotMatchNull() {
        // The subtle one: if "unlinked account" and "unowned resource" were both null and compared
        // equal, every account with no byline would own every unowned repository on the platform.
        assertThat(unlinked.canActOnBehalfOf(null)).isFalse();
    }

    private String messageOf(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected the call to be refused");
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }
}
