package com.knowledge.platform.ingestion.pipeline.handler;

import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.author.service.AuthorService;
import com.knowledge.platform.content.model.dto.Frontmatter;
import com.knowledge.platform.ingestion.model.entity.IngestionSeverity;
import com.knowledge.platform.ingestion.pipeline.HandlerOrder;
import com.knowledge.platform.ingestion.pipeline.IngestionContext;
import com.knowledge.platform.ingestion.pipeline.IngestionHandler;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Resolves the metadata that persistence needs: who wrote this.
 *
 * <p><b>Owned repository</b> (it has an owning byline): every article is attributed to the owner,
 * whatever the front matter says. Anyone who signs in may connect their own repository, so letting
 * {@code author: Anita Rao} decide the byline would let anyone publish under anyone's name -- and
 * hand that person, not the publisher, control of the article. A differing front-matter author is
 * recorded as an INFO diagnostic so the author can see why it was not used.
 *
 * <p><b>Unowned repository</b> (connected by an administrator, typically multi-author): the
 * document's front matter, then the repository's display name as a last resort. Only administrators
 * can create these, so the front matter is trusted. The fallback exists so connecting a repository of
 * existing Markdown -- which typically has no {@code author} front matter -- needs no editing pass.
 *
 * <p>See {@code .claude/specs/self-serve-authoring.md} (D4).
 */
@Slf4j
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

    static final String IGNORED_AUTHOR_CODE = "AUTHOR_FROM_OWNER";

    @Override
    public void handle(IngestionContext context) {
        Frontmatter frontmatter = context.requireDocument().frontmatter();
        Optional<String> declaredAuthor = frontmatter.first(AUTHOR_KEY);
        UUID ownerAuthorId = context.repository().getOwnerAuthorId();

        if (ownerAuthorId != null) {
            Author owner = authorService.requireById(ownerAuthorId);
            declaredAuthor
                    .filter(name -> !name.equalsIgnoreCase(owner.getDisplayName()))
                    .ifPresent(name -> {
                        log.debug("Ignoring front-matter author '{}' in {}; repository is owned by '{}'",
                                name, context.sourceFile().path(), owner.getSlug());
                        context.addDiagnostic(IngestionSeverity.INFO, IGNORED_AUTHOR_CODE,
                                "Front-matter author '" + name + "' was not used: articles from this "
                                        + "repository are published under its owner, '"
                                        + owner.getDisplayName() + "'.");
                    });
            context.setAuthor(owner);
            return;
        }

        // Unowned, therefore administrator-managed: the front matter is trusted. Creating an
        // identity from the repository's display name is the last resort, and it is preferable to
        // failing -- an approximate byline is correctable, a failed ingestion leaves nothing.
        Author author = declaredAuthor
                .map(name -> authorService.findOrCreateByName(
                        name, frontmatter.first(AUTHOR_EMAIL_KEY).orElse(null)))
                .orElseGet(() -> authorService.findOrCreateByName(context.repository().getDisplayName(), null));
        context.setAuthor(author);
    }
}
