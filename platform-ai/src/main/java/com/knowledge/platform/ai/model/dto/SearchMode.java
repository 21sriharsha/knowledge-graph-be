package com.knowledge.platform.ai.model.dto;

/** Which retrieval families the planner should draw on. */
public enum SearchMode {
    /** Keyword and phrase matching only. Right for exact identifiers, error strings, API names. */
    LEXICAL,
    /** Vector similarity only. Right for conceptual questions with no shared vocabulary. */
    SEMANTIC,
    /** Both, combined by the ranker. The default, and correct for most real queries. */
    HYBRID
}
