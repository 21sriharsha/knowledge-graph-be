package com.knowledge.platform.search.delegate;

import com.knowledge.platform.search.model.dto.SearchSuggestion;
import com.knowledge.platform.search.model.request.SearchRequest;
import com.knowledge.platform.search.model.response.SearchResponse;
import com.knowledge.platform.search.service.SearchService;
import java.util.List;
import org.springframework.stereotype.Component;

/** Translates between the search HTTP contract and the search domain. */
@Component
public class SearchDelegate {

    private final SearchService searchService;

    public SearchDelegate(SearchService searchService) {
        this.searchService = searchService;
    }

    public SearchResponse search(SearchRequest request) {
        return searchService.search(
                request.query(),
                request.page() == null ? 0 : request.page(),
                request.size() == null ? 0 : request.size(),
                request.understand());
    }

    public List<SearchSuggestion> suggest(String query, int limit) {
        return searchService.suggest(query, limit);
    }
}
