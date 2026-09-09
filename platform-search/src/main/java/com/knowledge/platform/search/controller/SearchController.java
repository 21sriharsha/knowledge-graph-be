package com.knowledge.platform.search.controller;

import com.knowledge.platform.search.delegate.SearchDelegate;
import com.knowledge.platform.search.model.dto.SearchSuggestion;
import com.knowledge.platform.search.model.request.SearchRequest;
import com.knowledge.platform.search.model.response.SearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public search. */
@RestController
@RequestMapping("/api/search")
@Validated
@Tag(name = "Search", description = "Hybrid lexical, semantic and metadata retrieval")
public class SearchController {

    private final SearchDelegate searchDelegate;

    public SearchController(SearchDelegate searchDelegate) {
        this.searchDelegate = searchDelegate;
    }

    @GetMapping
    @Operation(summary = "Search the knowledge base",
            description = """
                    Natural-language queries are interpreted into a structured intent before retrieval;
                    simple queries bypass that step entirely. The response reports how the query was
                    understood and which retrieval mode ran, so an unexpected result set can be
                    explained without reading the logs.
                    """)
    public SearchResponse search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "0") int size,
            @Parameter(description = "Force query understanding on or off; omit to let the analyzer decide")
            @RequestParam(required = false) Boolean understand) {
        return searchDelegate.search(new SearchRequest(q, page, size, understand));
    }

    @GetMapping("/suggest")
    @Operation(summary = "Suggestions for a partially typed query",
            description = """
                    Prefix and substring matches on article titles and author names, for a search
                    box's typeahead. Deliberately not a shortened search: full-text retrieval matches
                    stemmed whole words and so returns nothing for a word the reader has not finished
                    typing. No query understanding runs here -- there is nothing to interpret in three
                    characters. A query shorter than two characters returns an empty list.
                    """)
    public List<SearchSuggestion> suggest(
            @RequestParam String q,
            @RequestParam(defaultValue = "6") @Min(1) @Max(10) int limit) {
        return searchDelegate.suggest(q, limit);
    }
}
