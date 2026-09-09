package com.knowledge.platform.ai.model.dto;

/** What kind of thing the reader is looking for. */
public enum QueryIntentType {
    /** The default and overwhelmingly common case: find articles. */
    ARTICLE_SEARCH,
    /** "who writes about X", "articles by Harsh" -- the author is the subject, not a filter. */
    AUTHOR_SEARCH,
    /** "what is there on Kubernetes" -- browse a subject area. */
    TOPIC_SEARCH,
    /** The model could not classify the query. Planning treats this exactly like ARTICLE_SEARCH. */
    UNKNOWN
}
