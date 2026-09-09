package com.knowledge.platform.content.service;

import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.content.model.entity.Tag;
import com.knowledge.platform.content.model.entity.Topic;
import java.util.List;
import java.util.Set;

/**
 * Resolves the tag and topic names an author wrote into stored taxonomy entities.
 *
 * <p>Tags and topics are created on demand. An author adding {@code tags: [pgvector]} to frontmatter
 * should not have to pre-register the tag somewhere, and requiring it would make the taxonomy a
 * gate on publishing rather than a description of what has been published.
 *
 * <p>Names are matched by slug, so {@code PostgreSQL}, {@code postgresql} and {@code Postgre SQL}
 * collapse to one tag rather than three. The first spelling seen wins as the display name.
 */
public interface TaxonomyService {

    Set<Tag> resolveOrCreateTags(List<String> names);

    Set<Topic> resolveOrCreateTopics(List<String> names);

    Topic requireTopicBySlug(Slug slug);

    Tag requireTagBySlug(Slug slug);

    List<Topic> findAllTopics();

    List<Tag> findAllTags();
}
