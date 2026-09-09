package com.knowledge.platform.ingestion.pipeline;

import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.content.model.dto.CanonicalDocument;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.ingestion.model.entity.IngestionEvent;
import com.knowledge.platform.ingestion.model.entity.IngestionRun;
import com.knowledge.platform.ingestion.model.entity.IngestionSeverity;
import com.knowledge.platform.source.model.dto.SourceFile;
import com.knowledge.platform.source.model.entity.SourceRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The state one document carries through the pipeline.
 *
 * <p>Handlers read what earlier handlers produced and add their own. Mutable and single-threaded by
 * construction: one context belongs to one document on one thread, and it is never shared or reused.
 *
 * <p>Two flags drive control flow, and the distinction between them matters:
 *
 * <ul>
 *   <li>{@link #isSkipped()} -- the document is fine but needs no further work, because its content
 *       hash matches what is already stored. The remaining handlers are bypassed, which is what makes
 *       a replayed webhook cheap rather than merely harmless.
 *   <li>{@link #isFailed()} -- the document could not be processed. Recorded as a diagnostic and the
 *       run continues with the next document, because one bad file must not stop a repository sync.
 * </ul>
 */
public class IngestionContext {

    private final IngestionRun run;
    private final SourceRepository repository;
    private final SourceFile sourceFile;
    private final String contentHash;

    private CanonicalDocument document;
    private Author author;
    private Article article;
    private boolean contentChanged;
    private boolean skipped;
    private boolean failed;

    private final List<IngestionEvent> diagnostics = new ArrayList<>();

    /** Slugs of articles whose read models this document's change invalidates. */
    private final Set<String> invalidatedArticleSlugs = new LinkedHashSet<>();

    public IngestionContext(
            IngestionRun run, SourceRepository repository, SourceFile sourceFile, String contentHash) {
        this.run = run;
        this.repository = repository;
        this.sourceFile = sourceFile;
        this.contentHash = contentHash;
    }

    public IngestionRun run() {
        return run;
    }

    public SourceRepository repository() {
        return repository;
    }

    public SourceFile sourceFile() {
        return sourceFile;
    }

    public String contentHash() {
        return contentHash;
    }

    public Optional<CanonicalDocument> document() {
        return Optional.ofNullable(document);
    }

    public CanonicalDocument requireDocument() {
        if (document == null) {
            throw new IllegalStateException(
                    "No parsed document in context; a handler ran out of order");
        }
        return document;
    }

    public void setDocument(CanonicalDocument document) {
        this.document = document;
    }

    public Optional<Author> author() {
        return Optional.ofNullable(author);
    }

    public Author requireAuthor() {
        if (author == null) {
            throw new IllegalStateException("No author in context; a handler ran out of order");
        }
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }

    public Optional<Article> article() {
        return Optional.ofNullable(article);
    }

    public Article requireArticle() {
        if (article == null) {
            throw new IllegalStateException(
                    "No persisted article in context; a handler ran out of order");
        }
        return article;
    }

    public void setArticle(Article article) {
        this.article = article;
    }

    public boolean isContentChanged() {
        return contentChanged;
    }

    public void setContentChanged(boolean contentChanged) {
        this.contentChanged = contentChanged;
    }

    public boolean isSkipped() {
        return skipped;
    }

    /** Marks the document as needing no further work, and records why. */
    public void skip(String reason) {
        this.skipped = true;
        addDiagnostic(IngestionSeverity.INFO, IngestionEvent.CODE_UNCHANGED, reason);
    }

    public boolean isFailed() {
        return failed;
    }

    /** Marks the document as unprocessable, and records why. */
    public void fail(String code, String message) {
        this.failed = true;
        addDiagnostic(IngestionSeverity.ERROR, code, message);
    }

    public void addDiagnostic(IngestionSeverity severity, String code, String message) {
        diagnostics.add(new IngestionEvent(
                run.getId(), sourceFile.path(),
                article == null ? null : article.getSlug(),
                severity, code, message));
    }

    public List<IngestionEvent> diagnostics() {
        return List.copyOf(diagnostics);
    }

    public void invalidateArticle(String slug) {
        if (slug != null) {
            invalidatedArticleSlugs.add(slug);
        }
    }

    public Set<String> invalidatedArticleSlugs() {
        return Set.copyOf(invalidatedArticleSlugs);
    }

    /** Whether the remaining handlers should run. */
    public boolean shouldContinue() {
        return !skipped && !failed;
    }
}
