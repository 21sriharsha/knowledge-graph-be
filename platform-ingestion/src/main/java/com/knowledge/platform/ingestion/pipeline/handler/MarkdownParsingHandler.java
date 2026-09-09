package com.knowledge.platform.ingestion.pipeline.handler;

import com.knowledge.platform.content.markdown.CanonicalDocumentFactory;
import com.knowledge.platform.content.model.dto.CanonicalDocument;
import com.knowledge.platform.ingestion.model.entity.IngestionEvent;
import com.knowledge.platform.ingestion.model.entity.IngestionSeverity;
import com.knowledge.platform.ingestion.pipeline.HandlerOrder;
import com.knowledge.platform.ingestion.pipeline.IngestionContext;
import com.knowledge.platform.ingestion.pipeline.IngestionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Parses Markdown into the canonical document every later handler works from. */
@Component
@Order(HandlerOrder.MARKDOWN_PARSING)
public class MarkdownParsingHandler implements IngestionHandler {

    private final CanonicalDocumentFactory documentFactory;

    public MarkdownParsingHandler(CanonicalDocumentFactory documentFactory) {
        this.documentFactory = documentFactory;
    }

    @Override
    public String name() {
        return "parse-markdown";
    }

    @Override
    public void handle(IngestionContext context) {
        CanonicalDocument document = documentFactory.create(context.sourceFile().content());

        if (document.title() == null || document.title().isBlank()) {
            // A title is the article's identity: it produces the slug, and without one there is
            // nothing to link to or navigate by.
            context.fail(IngestionEvent.CODE_MISSING_TITLE,
                    "No title: add a 'title' to the frontmatter or start the file with a heading");
            return;
        }

        if (document.hasDroppedRawHtml()) {
            // Reported rather than silent. Dropping raw HTML is correct -- the backend emits no
            // markup, and forwarding unsanitised HTML would make the frontend a security boundary --
            // but an author whose content vanished deserves to be told.
            context.addDiagnostic(IngestionSeverity.WARNING, IngestionEvent.CODE_RAW_HTML_DROPPED,
                    document.droppedRawHtml().size()
                            + " raw HTML block(s) were removed. Express the content in Markdown "
                            + "instead; the platform does not render embedded HTML.");
        }

        context.setDocument(document);
    }
}
