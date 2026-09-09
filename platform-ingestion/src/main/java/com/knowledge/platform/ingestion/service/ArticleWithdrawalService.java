package com.knowledge.platform.ingestion.service;

import com.knowledge.platform.content.model.entity.Article;

/**
 * Withdraws an article whose source file was deleted.
 *
 * <p>Archived rather than deleted, and the ordering matters. The article is archived first so it stops
 * being publicly readable immediately; its derived representations are then torn down. Every inbound
 * link becomes UNRESOLVED rather than disappearing, so the articles that referenced it show a broken
 * reference -- which is the truth -- instead of silently losing a sentence's worth of the author's
 * meaning. Restoring the file restores the article and re-resolves every link to it.
 */
public interface ArticleWithdrawalService {

    void withdraw(Article article);
}
