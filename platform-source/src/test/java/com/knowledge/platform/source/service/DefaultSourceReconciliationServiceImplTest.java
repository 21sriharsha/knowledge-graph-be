package com.knowledge.platform.source.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledge.platform.source.integration.adapter.SourceAdapterException;
import com.knowledge.platform.source.model.dto.SourceSyncRequestedEvent;
import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.model.entity.SourceType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Reconciliation is the fallback for webhooks that never arrived. Its value is entirely in the cases
 * where something has gone wrong quietly, so those are what these tests describe.
 */
@ExtendWith(MockitoExtension.class)
class DefaultSourceReconciliationServiceImplTest {

    @Mock
    private SourceService sourceService;

    @Captor
    private ArgumentCaptor<SourceSyncRequestedEvent> published;

    private SourceReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultSourceReconciliationServiceImpl(sourceService);
    }

    @Test
    @DisplayName("a repository whose head has moved is synced")
    void requestsASyncWhenTheRepositoryIsBehind() {
        SourceRepository repository = repository("rev-1");
        when(sourceService.findActive()).thenReturn(List.of(repository));
        when(sourceService.resolveCurrentRevision(repository)).thenReturn(Optional.of("rev-2"));

        assertThat(service.reconcile()).isEqualTo(1);

        verify(sourceService).publishSync(published.capture());
        assertThat(published.getValue().revision()).isEqualTo("rev-2");
        assertThat(published.getValue().trigger())
                .isEqualTo(SourceSyncRequestedEvent.Trigger.SCHEDULED);
        assertThat(published.getValue().fullResync()).isTrue();
    }

    @Test
    @DisplayName("a repository already at head costs one API call and nothing else")
    void doesNothingWhenUpToDate() {
        SourceRepository repository = repository("rev-1");
        when(sourceService.findActive()).thenReturn(List.of(repository));
        when(sourceService.resolveCurrentRevision(repository)).thenReturn(Optional.of("rev-1"));

        assertThat(service.reconcile()).isZero();

        verify(sourceService, never()).publishSync(any());
    }

    @Test
    @DisplayName("a repository that has never synced is behind by definition")
    void syncsARepositoryThatHasNeverBeenSynced() {
        SourceRepository repository = repository(null);
        when(sourceService.findActive()).thenReturn(List.of(repository));
        when(sourceService.resolveCurrentRevision(repository)).thenReturn(Optional.of("rev-1"));

        assertThat(service.reconcile()).isEqualTo(1);

        verify(sourceService).publishSync(any());
    }

    @Test
    @DisplayName("one unreachable provider does not stop the others being reconciled")
    void continuesPastAnUnreachableProvider() {
        SourceRepository broken = repository("rev-1");
        SourceRepository healthy = repository("rev-1");
        when(sourceService.findActive()).thenReturn(List.of(broken, healthy));
        when(sourceService.resolveCurrentRevision(broken))
                .thenThrow(new SourceAdapterException(SourceType.GITHUB, "unreachable", true, null));
        when(sourceService.resolveCurrentRevision(healthy)).thenReturn(Optional.of("rev-2"));

        assertThat(service.reconcile()).isEqualTo(1);

        verify(sourceService).publishSync(any());
    }

    @Test
    @DisplayName("a provider that cannot resolve a head is skipped rather than resynced blindly")
    void skipsWhenNoHeadRevisionIsAvailable() {
        SourceRepository repository = repository("rev-1");
        when(sourceService.findActive()).thenReturn(List.of(repository));
        when(sourceService.resolveCurrentRevision(repository)).thenReturn(Optional.empty());

        assertThat(service.reconcile()).isZero();

        verify(sourceService, never()).publishSync(any());
    }

    @Test
    void doesNothingWithNoConnectedRepositories() {
        when(sourceService.findActive()).thenReturn(List.of());

        assertThat(service.reconcile()).isZero();
    }

    private SourceRepository repository(String lastSyncedRevision) {
        SourceRepository repository = SourceRepository.create(
                SourceType.GITHUB, "Handbook", "acme", "handbook", null, "main", "docs");
        if (lastSyncedRevision != null) {
            repository.recordSync(lastSyncedRevision);
        }
        return repository;
    }
}
