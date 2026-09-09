package com.knowledge.platform.graph.service;

import com.knowledge.platform.content.model.dto.ExtractedLink;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.graph.model.entity.ArticleLink;
import java.util.List;
import java.util.UUID;

/**
 * Turns the links extracted from a document into graph edges, and keeps them correct as content
 * around them changes.
 *
 * <p>Resolution is deterministic: a reference resolves iff its normalized slug equals a stored
 * article's slug. There is no fuzzy matching and no "did you mean" -- an author writing
 * {@code [[Postgres Indexes]]} when the article is "PostgreSQL Indexes" should be told their link is
 * broken, not quietly pointed at something the platform guessed.
 */
public interface LinkResolutionService {

    /**
     * Replaces an article's outbound edges with those extracted from its current content.
     *
     * @return the references that did not resolve, for ingestion to report as diagnostics
     */
    List<String> replaceOutboundLinks(UUID articleId, List<ExtractedLink> extracted);

    /**
     * Binds every dangling reference to a newly available article.
     *
     * <p>Called when an article is published. Without this, a reference written before its target
     * existed would stay broken until the referring article happened to be re-ingested -- which for
     * a stable document might be never.
     */
    int resolvePendingLinksTo(Article article);

    /** Breaks inbound edges when an article stops being publicly resolvable. */
    int unresolveLinksTo(UUID articleId);

    List<ArticleLink> outboundLinks(UUID articleId);

    List<ArticleLink> backlinks(UUID articleId);

    List<ArticleLink> allUnresolved();
}
