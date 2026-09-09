package com.knowledge.platform.ingestion.pipeline.handler;

import com.knowledge.platform.ingestion.pipeline.HandlerOrder;
import com.knowledge.platform.ingestion.pipeline.IngestionContext;
import com.knowledge.platform.ingestion.pipeline.IngestionHandler;
import com.knowledge.platform.search.service.SearchIndexService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Writes the lexical search document.
 *
 * <p>Runs inline rather than as a job. It is a single upsert against PostgreSQL, and an article that
 * is published but cannot be found by searching its own title is a defect a reader would notice
 * immediately -- unlike a missing embedding, which merely reduces recall.
 */
@Component
@Order(HandlerOrder.SEARCH_INDEX)
public class SearchIndexHandler implements IngestionHandler {

    private final SearchIndexService searchIndexService;

    public SearchIndexHandler(SearchIndexService searchIndexService) {
        this.searchIndexService = searchIndexService;
    }

    @Override
    public String name() {
        return "index-search";
    }

    @Override
    public void handle(IngestionContext context) {
        searchIndexService.index(
                context.requireArticle(),
                context.requireDocument().plainText(),
                context.requireAuthor().getDisplayName());
    }
}
