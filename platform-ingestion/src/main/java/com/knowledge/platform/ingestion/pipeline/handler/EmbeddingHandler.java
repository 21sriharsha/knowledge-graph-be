package com.knowledge.platform.ingestion.pipeline.handler;

import com.knowledge.platform.ingestion.pipeline.HandlerOrder;
import com.knowledge.platform.ingestion.pipeline.IngestionContext;
import com.knowledge.platform.ingestion.pipeline.IngestionHandler;
import com.knowledge.platform.search.service.SearchIndexService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Schedules embedding generation.
 *
 * <p>Schedules rather than performs. Embedding requires an inference call that can take seconds and
 * can fail, and the reliability requirement is explicit that an article must remain publishable when
 * embedding generation fails. Doing it here on the ingestion thread would make every publish as slow
 * as the model, and every model outage a publishing outage.
 *
 * <p>Its position after the search index handler matters too: lexical search works for the article
 * from the moment it is published, whether or not the vector ever arrives.
 */
@Component
@Order(HandlerOrder.EMBEDDING)
public class EmbeddingHandler implements IngestionHandler {

    private final SearchIndexService searchIndexService;

    public EmbeddingHandler(SearchIndexService searchIndexService) {
        this.searchIndexService = searchIndexService;
    }

    @Override
    public String name() {
        return "schedule-embedding";
    }

    @Override
    public void handle(IngestionContext context) {
        searchIndexService.scheduleEmbedding(
                context.requireArticle().getId(),
                context.requireDocument().plainText(),
                context.contentHash());
    }
}
