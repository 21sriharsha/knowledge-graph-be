package com.knowledge.platform.ingestion.pipeline.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.author.service.AuthorService;
import com.knowledge.platform.content.model.dto.CanonicalDocument;
import com.knowledge.platform.content.model.dto.Frontmatter;
import com.knowledge.platform.ingestion.model.entity.IngestionEvent;
import com.knowledge.platform.ingestion.model.entity.IngestionRun;
import com.knowledge.platform.ingestion.model.entity.IngestionSeverity;
import com.knowledge.platform.ingestion.pipeline.IngestionContext;
import com.knowledge.platform.source.model.dto.SourceFile;
import com.knowledge.platform.source.model.dto.SourceSyncRequestedEvent;
import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.model.entity.SourceType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Article attribution (self-serve authoring, AC-8). */
class MetadataExtractionHandlerTest {

    private final AuthorService authors = mock(AuthorService.class);
    private final MetadataExtractionHandler handler = new MetadataExtractionHandler(authors);

    private static IngestionContext context(UUID ownerAuthorId, Map<String, List<String>> frontmatter) {
        SourceRepository repository = SourceRepository.create(
                SourceType.GITHUB, "Handbook", "acme", "handbook", null, "main", "docs");
        repository.updateSettings(null, null, null, null, ownerAuthorId);
        IngestionRun run = IngestionRun.start(UUID.randomUUID(), SourceSyncRequestedEvent.Trigger.MANUAL, "rev-1");
        IngestionContext context = new IngestionContext(
                run, repository, new SourceFile("docs/a.md", "# A", "rev-1", "blob-1"), "hash-1");
        context.setDocument(new CanonicalDocument(
                "A", "S", new Frontmatter(frontmatter), List.of(), List.of(), List.of(), "", 0, 0, List.of()));
        return context;
    }

    @Test
    @DisplayName("AC-8 an owned repository publishes under its owner, whatever the front matter claims")
    void ownedRepositoryUsesOwner() {
        Author owner = Author.create("jane-doe", "Jane Doe");
        when(authors.requireById(owner.getId())).thenReturn(owner);
        IngestionContext context = context(owner.getId(), Map.of("author", List.of("Anita Rao")));

        handler.handle(context);

        assertThat(context.requireAuthor()).isSameAs(owner);
        verify(authors, never()).findOrCreateByName(any(), any());
        assertThat(context.diagnostics()).extracting(IngestionEvent::getCode, IngestionEvent::getSeverity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        MetadataExtractionHandler.IGNORED_AUTHOR_CODE, IngestionSeverity.INFO));
    }

    @Test
    @DisplayName("AC-8 front matter naming the owner itself raises no diagnostic")
    void ownerNamedInFrontMatter() {
        Author owner = Author.create("jane-doe", "Jane Doe");
        when(authors.requireById(owner.getId())).thenReturn(owner);
        IngestionContext context = context(owner.getId(), Map.of("author", List.of("jane doe")));

        handler.handle(context);

        assertThat(context.requireAuthor()).isSameAs(owner);
        assertThat(context.diagnostics()).isEmpty();
    }

    @Test
    @DisplayName("AC-8 an unowned (admin-managed) repository still trusts front matter")
    void unownedRepositoryTrustsFrontMatter() {
        Author anita = Author.create("anita-rao", "Anita Rao");
        when(authors.findOrCreateByName("Anita Rao", "anita@example.com")).thenReturn(anita);
        IngestionContext context = context(null, Map.of(
                "author", List.of("Anita Rao"), "author_email", List.of("anita@example.com")));

        handler.handle(context);

        assertThat(context.requireAuthor()).isSameAs(anita);
    }

    @Test
    @DisplayName("an unowned repository without front-matter author falls back to its display name")
    void unownedFallsBackToDisplayName() {
        Author handbook = Author.create("handbook", "Handbook");
        when(authors.findOrCreateByName("Handbook", null)).thenReturn(handbook);
        IngestionContext context = context(null, Map.of());

        handler.handle(context);

        assertThat(context.requireAuthor()).isSameAs(handbook);
    }
}
