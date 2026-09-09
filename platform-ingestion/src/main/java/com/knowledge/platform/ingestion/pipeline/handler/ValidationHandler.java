package com.knowledge.platform.ingestion.pipeline.handler;

import com.knowledge.platform.ingestion.model.entity.IngestionEvent;
import com.knowledge.platform.ingestion.pipeline.HandlerOrder;
import com.knowledge.platform.ingestion.pipeline.IngestionContext;
import com.knowledge.platform.ingestion.pipeline.IngestionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Rejects documents that cannot become articles, before any work is spent on them.
 *
 * <p>Cheap checks first, and no I/O: this handler exists so the expensive steps behind it never run
 * on input that was never going to produce an article.
 */
@Component
@Order(HandlerOrder.VALIDATION)
public class ValidationHandler implements IngestionHandler {

    /** Below this a file has no article in it -- an empty stub or a placeholder. */
    private static final int MINIMUM_CONTENT_LENGTH = 10;

    @Override
    public String name() {
        return "validate";
    }

    @Override
    public void handle(IngestionContext context) {
        String content = context.sourceFile().content();

        if (content == null || content.isBlank()) {
            context.fail(IngestionEvent.CODE_PARSE_FAILED, "The file is empty");
            return;
        }
        if (content.strip().length() < MINIMUM_CONTENT_LENGTH) {
            context.fail(IngestionEvent.CODE_PARSE_FAILED,
                    "The file has too little content to be an article");
            return;
        }
        if (content.indexOf('\0') >= 0) {
            // A NUL byte means the provider handed back binary content as text. Parsing it would
            // produce nonsense and storing it would corrupt the search index.
            context.fail(IngestionEvent.CODE_PARSE_FAILED,
                    "The file appears to be binary, not Markdown");
        }
    }
}
