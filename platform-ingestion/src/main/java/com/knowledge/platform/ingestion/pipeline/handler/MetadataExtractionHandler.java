package com.knowledge.platform.ingestion.pipeline.handler;

import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.author.service.AuthorService;
import com.knowledge.platform.content.model.dto.Frontmatter;
import com.knowledge.platform.ingestion.pipeline.HandlerOrder;
import com.knowledge.platform.ingestion.pipeline.IngestionContext;
import com.knowledge.platform.ingestion.pipeline.IngestionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Resolves the metadata that persistence needs: who wrote this.
 *
 * <p>Author precedence: the document's frontmatter, then the repository's configured owning author,
 * then the repository's display name as a last resort. The fallback chain exists so that connecting
 * a repository of existing Markdown -- which typically has no {@code author} frontmatter anywhere --
 * works without an editing pass over every file first.
 */
@Component
@Order(HandlerOrder.METADATA_EXTRACTION)
public class MetadataExtractionHandler implements IngestionHandler {

    private static final String AUTHOR_KEY = "author";
    private static final String AUTHOR_EMAIL_KEY = "author_email";

    private final AuthorService authorService;

    public MetadataExtractionHandler(AuthorService authorService) {
        this.authorService = authorService;
    }

    @Override
    public String name() {
        return "extract-metadata";
    }

    @Override
    public void handle(IngestionContext context) {
        Frontmatter frontmatter = context.requireDocument().frontmatter();

        Author author = frontmatter.first(AUTHOR_KEY)
                .map(name -> authorService.findOrCreateByName(
                        name, frontmatter.first(AUTHOR_EMAIL_KEY).orElse(null)))
                .orElseGet(() -> resolveRepositoryAuthor(context));

        context.setAuthor(author);
    }

    /**
     * Falls back to the repository's owning author.
     *
     * <p>Creating an identity from the repository's display name is the last resort, and it is
     * preferable to failing: an article with an approximate byline is publishable and correctable,
     * whereas a failed ingestion leaves the reader with nothing.
     */
    private Author resolveRepositoryAuthor(IngestionContext context) {
        if (context.repository().getOwnerAuthorId() != null) {
            return authorService.requireById(context.repository().getOwnerAuthorId());
        }
        return authorService.findOrCreateByName(context.repository().getDisplayName(), null);
    }
}
