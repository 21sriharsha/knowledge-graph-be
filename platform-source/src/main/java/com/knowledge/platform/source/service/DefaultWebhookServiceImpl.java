package com.knowledge.platform.source.service;

import com.knowledge.platform.source.integration.SourceIntegrationFactory;
import com.knowledge.platform.source.integration.strategy.SourceIntegrationStrategy;
import com.knowledge.platform.source.model.dto.PushNotification;
import com.knowledge.platform.source.model.dto.SourceSyncRequestedEvent;
import com.knowledge.platform.source.model.dto.WebhookOutcome;
import com.knowledge.platform.source.model.dto.WebhookRequest;
import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.model.entity.WebhookDelivery;
import com.knowledge.platform.source.repository.WebhookDeliveryRepository;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default WebhookService.
 *
 * <p>See {@link WebhookService} for what this provides and why it exists.
 */
@Slf4j
@Service
public class DefaultWebhookServiceImpl implements WebhookService {

    private final SourceService sourceService;
    private final SourceIntegrationFactory integrationFactory;
    private final WebhookDeliveryRepository deliveries;

    public DefaultWebhookServiceImpl(
            SourceService sourceService,
            SourceIntegrationFactory integrationFactory,
            WebhookDeliveryRepository deliveries) {
        this.sourceService = sourceService;
        this.integrationFactory = integrationFactory;
        this.deliveries = deliveries;
    }

    @Override
    @Transactional
    public WebhookOutcome accept(UUID repositoryId, WebhookRequest request) {
        SourceRepository repository = sourceService.requireById(repositoryId);
        if (!repository.isActive()) {
            return WebhookOutcome.ignored("repository is not active");
        }

        SourceIntegrationStrategy strategy = integrationFactory.strategyFor(repository);
        WebhookOutcome outcome = strategy.interpretWebhook(repository, request);
        if (!outcome.isAccepted()) {
            return outcome;
        }

        PushNotification push = outcome.push();
        if (!recordDelivery(repository, push)) {
            return WebhookOutcome.ignored("delivery " + push.deliveryId() + " was already processed");
        }

        // A provider that reports which files changed lets ingestion process those files; one that
        // does not (Azure DevOps) forces a full walk. Asking the strategy keeps that decision with
        // the provider knowledge rather than inferring it from an empty list.
        boolean fullResync = !strategy.supportsIncrementalSync() || !push.hasPathDetail();

        sourceService.publishSync(new SourceSyncRequestedEvent(
                repository.getId(),
                push.revision(),
                SourceSyncRequestedEvent.Trigger.WEBHOOK,
                push.changedPaths(),
                push.removedPaths(),
                fullResync));

        return outcome;
    }

    /**
     * Claims a delivery id, returning false when it has already been seen.
     *
     * <p>The unique constraint is the real guard, not the existence check: two concurrent retries can
     * both pass a check and only one can win the insert. Catching the violation is therefore part of
     * the mechanism rather than defensive noise.
     */
    private boolean recordDelivery(SourceRepository repository, PushNotification push) {
        if (push.deliveryId() == null || push.deliveryId().isBlank()) {
            // Some providers omit a delivery id. Ingestion is idempotent by content hash regardless,
            // so this costs a redundant pipeline run at worst, never duplicate content.
            log.debug("Webhook for {} carried no delivery id; relying on content-hash idempotency",
                    repository);
            return true;
        }
        if (deliveries.existsByRepositoryIdAndDeliveryId(repository.getId(), push.deliveryId())) {
            return false;
        }
        try {
            deliveries.saveAndFlush(
                    new WebhookDelivery(repository.getId(), push.deliveryId(), "push"));
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("Concurrent delivery {} for {} lost the race; ignoring the duplicate",
                    push.deliveryId(), repository);
            return false;
        }
    }
}
