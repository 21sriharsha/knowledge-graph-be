package com.knowledge.platform.content.service;

import com.knowledge.platform.common.exception.NotFoundException;
import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.common.service.SlugPolicy;
import com.knowledge.platform.content.model.entity.Tag;
import com.knowledge.platform.content.model.entity.Topic;
import com.knowledge.platform.content.repository.TagRepository;
import com.knowledge.platform.content.repository.TopicRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default TaxonomyService.
 *
 * <p>See {@link TaxonomyService} for what this provides and why it exists.
 */
@Service
@Transactional(readOnly = true)
public class DefaultTaxonomyServiceImpl implements TaxonomyService {

    private final TagRepository tagRepository;
    private final TopicRepository topicRepository;
    private final SlugPolicy slugPolicy;

    public DefaultTaxonomyServiceImpl(
            TagRepository tagRepository, TopicRepository topicRepository, SlugPolicy slugPolicy) {
        this.tagRepository = tagRepository;
        this.topicRepository = topicRepository;
        this.slugPolicy = slugPolicy;
    }

    @Override
    @Transactional
    public Set<Tag> resolveOrCreateTags(List<String> names) {
        return resolveOrCreate(names, tagRepository::findBySlugIn, Tag::getSlug, Tag::create,
                tagRepository::saveAll);
    }

    @Override
    @Transactional
    public Set<Topic> resolveOrCreateTopics(List<String> names) {
        return resolveOrCreate(names, topicRepository::findBySlugIn, Topic::getSlug, Topic::create,
                topicRepository::saveAll);
    }

    @Override
    public Topic requireTopicBySlug(Slug slug) {
        return topicRepository.findBySlug(slug.value())
                .orElseThrow(() -> NotFoundException.of("Topic", slug.value()));
    }

    @Override
    public Tag requireTagBySlug(Slug slug) {
        return tagRepository.findBySlug(slug.value())
                .orElseThrow(() -> NotFoundException.of("Tag", slug.value()));
    }

    @Override
    public List<Topic> findAllTopics() {
        return topicRepository.findAllByOrderByNameAsc();
    }

    @Override
    public List<Tag> findAllTags() {
        return tagRepository.findAllByOrderByNameAsc();
    }

    /**
     * Shared resolve-or-create for both taxonomies.
     *
     * <p>Deliberately one bulk lookup and one bulk insert rather than a query per name: a document
     * with eight tags would otherwise cost sixteen round trips inside the ingestion transaction.
     */
    private <T> Set<T> resolveOrCreate(
            List<String> names,
            Function<List<String>, List<T>> findBySlugs,
            Function<T, String> slugOf,
            BiFunction<String, String, T> factory,
            Function<List<T>, List<T>> saveAll) {

        // LinkedHashMap preserves frontmatter order, which decides an article's primary topic.
        Map<String, String> slugToName = new LinkedHashMap<>();
        for (String name : names) {
            Slug slug = slugPolicy.slugifyOrNull(name);
            if (slug != null) {
                slugToName.putIfAbsent(slug.value(), name.trim());
            }
        }
        if (slugToName.isEmpty()) {
            return Set.of();
        }

        Map<String, T> existing = new LinkedHashMap<>();
        findBySlugs.apply(List.copyOf(slugToName.keySet()))
                .forEach(entity -> existing.put(slugOf.apply(entity), entity));

        List<T> created = slugToName.entrySet().stream()
                .filter(entry -> !existing.containsKey(entry.getKey()))
                .map(entry -> factory.apply(entry.getKey(), entry.getValue()))
                .toList();
        if (!created.isEmpty()) {
            saveAll.apply(created).forEach(entity -> existing.put(slugOf.apply(entity), entity));
        }

        Set<T> result = new LinkedHashSet<>();
        slugToName.keySet().forEach(slug -> {
            T entity = existing.get(slug);
            if (entity != null) {
                result.add(entity);
            }
        });
        return result;
    }
}
