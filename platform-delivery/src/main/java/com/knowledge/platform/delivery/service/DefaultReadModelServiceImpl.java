package com.knowledge.platform.delivery.service;

import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.service.ArticleService;
import com.knowledge.platform.delivery.model.dto.ArticleReadModel;
import com.knowledge.platform.delivery.model.entity.ArticleReadModelRecord;
import com.knowledge.platform.delivery.repository.ArticleReadModelRepository;
import com.knowledge.platform.delivery.service.assembler.ArticleReadModelAssembler;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Default ReadModelService.
 *
 * <p>See {@link ReadModelService} for what this provides and why it exists.
 */
@Slf4j
@Service
public class DefaultReadModelServiceImpl implements ReadModelService {

    private final ArticleService articleService;
    private final ArticleReadModelAssembler assembler;
    private final ArticleReadModelRepository readModels;
    private final ObjectMapper objectMapper;

    public DefaultReadModelServiceImpl(
            ArticleService articleService,
            ArticleReadModelAssembler assembler,
            ArticleReadModelRepository readModels,
            ObjectMapper objectMapper) {
        this.articleService = articleService;
        this.assembler = assembler;
        this.readModels = readModels;
        this.objectMapper = objectMapper;
    }

    @Override
    @Cacheable(cacheNames = "articleReadModels", key = "#slug.value()")
    @Transactional(readOnly = true)
    public ArticleReadModel articleBySlug(Slug slug) {
        Article article = articleService.requirePublishedBySlug(slug);
        return readModels.findById(article.getId())
                .filter(record -> record.matches(
                        article.getContentHash(), ArticleReadModel.SCHEMA_VERSION))
                .flatMap(this::deserialize)
                .orElseGet(() -> {
                    log.debug("Read model for '{}' is absent or stale; assembling", slug.value());
                    return materialize(article);
                });
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "articleReadModels", key = "#article.slug")
    public ArticleReadModel materialize(Article article) {
        ArticleReadModel model = assembler.assemble(article);
        serialize(model).ifPresent(payload -> readModels.findById(article.getId())
                .map(record -> {
                    record.replace(
                            payload, article.getContentHash(), ArticleReadModel.SCHEMA_VERSION);
                    return record;
                })
                .or(() -> Optional.of(new ArticleReadModelRecord(
                        article.getId(),
                        payload,
                        article.getContentHash(),
                        ArticleReadModel.SCHEMA_VERSION)))
                .ifPresent(readModels::save));
        return model;
    }

    @Override
    @Transactional
    public void remove(UUID articleId) {
        readModels.deleteById(articleId);
    }

    private Optional<ArticleReadModel> deserialize(ArticleReadModelRecord record) {
        try {
            return Optional.of(objectMapper.readValue(record.getPayload(), ArticleReadModel.class));
        } catch (JacksonException e) {
            // A payload written by an older shape of the model. Falling back to assembly is correct
            // and self-healing: the rebuilt model is stored in the current shape.
            log.warn("Stored read model for {} could not be read; reassembling", record.getArticleId(), e);
            return Optional.empty();
        }
    }

    private Optional<String> serialize(ArticleReadModel model) {
        try {
            return Optional.of(objectMapper.writeValueAsString(model));
        } catch (JacksonException e) {
            // The model is still returned to the caller; only the persisted copy is skipped, so this
            // costs performance rather than correctness.
            log.error("Could not serialize the read model for '{}'; serving it without persisting",
                    model.slug(), e);
            return Optional.empty();
        }
    }
}
