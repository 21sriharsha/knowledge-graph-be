package com.knowledge.platform.delivery.service;

import com.knowledge.platform.common.model.PageResponse;
import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.delivery.model.dto.ArticleReference;
import com.knowledge.platform.delivery.model.dto.AuthorReadModel;
import com.knowledge.platform.delivery.model.dto.NavigationModel;
import com.knowledge.platform.delivery.model.dto.TagReadModel;
import com.knowledge.platform.delivery.model.dto.TaxonomyListing;
import com.knowledge.platform.delivery.model.dto.TopicReadModel;
import org.springframework.data.domain.Pageable;

/**
 * Read models for the browse surfaces: author, topic and tag pages, the taxonomy indexes, the
 * article archive, and site navigation.
 *
 * <p>These are assembled on demand and cached, rather than persisted the way article read models
 * are. The asymmetry is deliberate: an article read model is expensive (Markdown parse, graph walk)
 * and changes only when its article does, so it earns a table. These are a couple of indexed queries
 * and change whenever <em>any</em> article is published, so persisting them would mean invalidating
 * a stored row on every publish for very little saved work.
 */
public interface BrowseReadModelService {

    AuthorReadModel authorBySlug(Slug slug);

    TopicReadModel topicBySlug(Slug slug);

    NavigationModel navigation();

    /**
     * The tag landing page.
     *
     * <p>Symmetric with {@link #topicBySlug(Slug)}, and deliberately a separate method: a tag and a
     * topic that happen to share a slug are different pages showing different sets of articles.
     */
    TagReadModel tagBySlug(Slug slug);

    /** Every topic with published content, for the browse index. */
    TaxonomyListing listTopics();

    /** Every tag with published content, for the browse index. */
    TaxonomyListing listTags();

    /**
     * Published articles, newest first.
     *
     * <p>Paged rather than capped: this is the archive, and it is the one browse surface that has to
     * remain usable when the corpus is large.
     */
    PageResponse<ArticleReference> listArticles(Pageable pageable);
}
