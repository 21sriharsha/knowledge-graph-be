package com.knowledge.platform.delivery.service;

import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.delivery.model.dto.TrendingArticle;
import java.util.List;

/**
 * Readership: what has actually been read, and what is being read now.
 *
 * <p>This is the platform's only observed signal. Everything else it knows -- links, topics,
 * authorship -- was written down by someone; this is the one thing it learns by being used. That is
 * also why it is the only data here that cannot be rebuilt from the Markdown.
 *
 * <p>Counting is deliberately anonymous. Nothing recorded can answer who read what, only how often
 * something was read, which is the whole of what a trending list needs.
 */
public interface ArticleViewService {

    /**
     * Records one read of an article.
     *
     * <p>Called on the serving path, so it must not touch the database. Implementations buffer and
     * flush; a caller can assume this is cheap and non-blocking.
     */
    void recordView(Slug slug);

    /**
     * The most-read articles over the last {@code days} days.
     *
     * <p>Empty until something has actually been read. An empty list is the honest answer to "what
     * is trending" on a platform nobody has visited yet, and callers are expected to say so rather
     * than substitute an arbitrary list.
     */
    List<TrendingArticle> trending(int days, int limit);

    /** Writes buffered counts through to the database. */
    void flush();
}
