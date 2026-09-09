package com.knowledge.platform.source.integration.strategy;

import com.knowledge.platform.source.integration.adapter.SourceAdapter;
import com.knowledge.platform.source.model.dto.PushNotification;
import com.knowledge.platform.source.model.dto.RemoteEntry;
import com.knowledge.platform.source.model.dto.RepositoryDescriptor;
import com.knowledge.platform.source.model.dto.SourceFile;
import com.knowledge.platform.source.model.dto.WebhookOutcome;
import com.knowledge.platform.source.model.dto.WebhookRequest;
import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.service.SourceCredentialCipher;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractSourceIntegrationStrategy implements SourceIntegrationStrategy {

    private final SourceAdapter adapter;
    private final SourceCredentialCipher credentialCipher;

    protected AbstractSourceIntegrationStrategy(
            SourceAdapter adapter, SourceCredentialCipher credentialCipher) {
        this.adapter = adapter;
        this.credentialCipher = credentialCipher;
    }

    @Override
    public Optional<String> resolveCurrentRevision(SourceRepository repository) {
        return adapter.resolveHeadRevision(descriptorFor(repository), repository.getDefaultBranch());
    }

    @Override
    public List<SourceFile> fetchContentSnapshot(SourceRepository repository, String revision) {
        RepositoryDescriptor descriptor = descriptorFor(repository);
        List<RemoteEntry> entries =
                adapter.listTree(descriptor, revision, repository.getContentPath());

        List<SourceFile> files = new ArrayList<>();
        for (RemoteEntry entry : entries) {
            // What counts as content is a platform decision, applied identically for every provider.
            if (!entry.isMarkdown() || !repository.coversPath(entry.path())) {
                continue;
            }
            adapter.readFile(descriptor, entry.path(), revision).ifPresent(files::add);
        }
        log.debug("Fetched {} Markdown file(s) from {} at {}", files.size(), repository, revision);
        return files;
    }

    @Override
    public Optional<SourceFile> fetchFile(
            SourceRepository repository, String path, String revision) {
        if (!repository.coversPath(path)) {
            return Optional.empty();
        }
        return adapter.readFile(descriptorFor(repository), path, revision);
    }

    /**
     * Verify, then interpret. Never the other way round.
     *
     * <p>Parsing an unverified payload would mean running a JSON deserializer over attacker-supplied
     * bytes before establishing that the sender is who they claim to be. The ordering here is the
     * security property, not a stylistic choice.
     */
    @Override
    public WebhookOutcome interpretWebhook(SourceRepository repository, WebhookRequest request) {
        if (!repository.hasWebhookSecret()) {
            return WebhookOutcome.rejected("no webhook secret is configured for this repository");
        }

        String secret = credentialCipher.decrypt(repository.getWebhookSecretCiphertext());
        if (!adapter.verifyWebhook(request, secret)) {
            // Logged without the signature or any payload content: a rejected webhook is exactly the
            // case where the body is untrusted and must not be echoed into the log.
            log.warn("Rejected an unverified webhook for {}", repository);
            return WebhookOutcome.rejected("signature verification failed");
        }

        Optional<PushNotification> push = adapter.parsePush(request);
        if (push.isEmpty()) {
            return WebhookOutcome.ignored("event is not an actionable push");
        }

        PushNotification notification = push.get();
        if (!repository.getDefaultBranch().equals(notification.branch())) {
            return WebhookOutcome.ignored(
                    "push targets branch '" + notification.branch() + "', not the content branch");
        }
        return WebhookOutcome.accepted(notification);
    }

    /**
     * Builds the transient descriptor an adapter needs, decrypting the credential at the last
     * possible moment so the plaintext exists only for the duration of the call.
     */
    protected RepositoryDescriptor descriptorFor(SourceRepository repository) {
        return new RepositoryDescriptor(
                repository.getSourceType(),
                repository.getOwner(),
                repository.getRepository(),
                repository.getProject(),
                repository.getDefaultBranch(),
                repository.getContentPath(),
                repository.getApiBaseUrl(),
                credentialCipher.decrypt(repository.getAccessTokenCiphertext()));
    }

    protected SourceAdapter adapter() {
        return adapter;
    }
}
