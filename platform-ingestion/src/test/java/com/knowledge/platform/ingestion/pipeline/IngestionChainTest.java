package com.knowledge.platform.ingestion.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledge.platform.ingestion.model.entity.IngestionEvent;
import com.knowledge.platform.ingestion.model.entity.IngestionRun;
import com.knowledge.platform.ingestion.model.entity.IngestionSeverity;
import com.knowledge.platform.source.model.dto.SourceFile;
import com.knowledge.platform.source.model.dto.SourceSyncRequestedEvent;
import com.knowledge.platform.source.model.entity.SourceRepository;
import com.knowledge.platform.source.model.entity.SourceType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The chain's job is to be boring and predictable: run handlers in order, stop cleanly when a
 * document is skipped or fails, and never let one bad document abort a repository sync.
 */
class IngestionChainTest {

    @Test
    void runsEveryHandlerInOrder() {
        List<String> executed = new ArrayList<>();
        IngestionChain chain = chain(
                handler("first", context -> executed.add("first")),
                handler("second", context -> executed.add("second")),
                handler("third", context -> executed.add("third")));

        chain.execute(context());

        assertThat(executed).containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("a skipped document stops the chain: its derived state is already correct")
    void stopsAfterASkip() {
        List<String> executed = new ArrayList<>();
        IngestionChain chain = chain(
                handler("first", context -> executed.add("first")),
                handler("skipper", context -> context.skip("content unchanged")),
                handler("never", context -> executed.add("never")));

        IngestionContext context = context();
        chain.execute(context);

        assertThat(executed).containsExactly("first");
        assertThat(context.isSkipped()).isTrue();
        assertThat(context.isFailed()).isFalse();
        assertThat(context.diagnostics()).singleElement()
                .satisfies(event -> assertThat(event.getCode()).isEqualTo(IngestionEvent.CODE_UNCHANGED));
    }

    @Test
    void stopsAfterAFailure() {
        List<String> executed = new ArrayList<>();
        IngestionChain chain = chain(
                handler("first", context -> executed.add("first")),
                handler("failer", context -> context.fail(IngestionEvent.CODE_MISSING_TITLE, "no title")),
                handler("never", context -> executed.add("never")));

        IngestionContext context = context();
        chain.execute(context);

        assertThat(executed).containsExactly("first");
        assertThat(context.isFailed()).isTrue();
    }

    @Test
    @DisplayName("a handler throwing is contained: one bad document must not abort a repository sync")
    void containsAThrownException() {
        List<String> executed = new ArrayList<>();
        IngestionChain chain = chain(
                handler("first", context -> executed.add("first")),
                handler("boom", context -> {
                    throw new IllegalStateException("provider returned nonsense");
                }),
                handler("never", context -> executed.add("never")));

        IngestionContext context = context();
        chain.execute(context);

        assertThat(executed).containsExactly("first");
        assertThat(context.isFailed()).isTrue();
        assertThat(context.diagnostics()).singleElement().satisfies(event -> {
            assertThat(event.getCode()).isEqualTo(IngestionEvent.CODE_HANDLER_FAILED);
            assertThat(event.getSeverity()).isEqualTo(IngestionSeverity.ERROR);
            // The failing handler is named, so the diagnostic points at where to look.
            assertThat(event.getMessage()).contains("boom").contains("provider returned nonsense");
        });
    }

    @Test
    @DisplayName("a handler can opt into running for skipped or failed documents")
    void honoursAppliesTo() {
        List<String> executed = new ArrayList<>();
        IngestionHandler always = new IngestionHandler() {
            @Override
            public String name() {
                return "always";
            }

            @Override
            public void handle(IngestionContext context) {
                executed.add("always");
            }

            @Override
            public boolean appliesTo(IngestionContext context) {
                return true;
            }
        };

        IngestionChain chain = chain(
                handler("skipper", context -> context.skip("unchanged")),
                always);
        chain.execute(context());

        assertThat(executed).containsExactly("always");
    }

    @Test
    @DisplayName("the pipeline reports itself as assembled, not as documented")
    void reportsItsHandlerNames() {
        IngestionChain chain = chain(
                handler("validate", context -> { }),
                handler("parse", context -> { }));

        assertThat(chain.handlerNames()).containsExactly("validate", "parse");
    }

    @Test
    void distinguishesAuthorFacingDiagnosticsFromInformationalOnes() {
        IngestionContext informational = context();
        informational.addDiagnostic(IngestionSeverity.INFO, IngestionEvent.CODE_UNCHANGED, "no change");
        assertThat(IngestionChain.hasAuthorFacingDiagnostics(informational)).isFalse();

        IngestionContext warned = context();
        warned.addDiagnostic(IngestionSeverity.WARNING, IngestionEvent.CODE_UNRESOLVED_LINK, "dangling");
        assertThat(IngestionChain.hasAuthorFacingDiagnostics(warned)).isTrue();
    }

    private IngestionChain chain(IngestionHandler... handlers) {
        return new IngestionChain(List.of(handlers), new SimpleMeterRegistry());
    }

    private IngestionHandler handler(String name, Consumer<IngestionContext> action) {
        return new IngestionHandler() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void handle(IngestionContext context) {
                action.accept(context);
            }
        };
    }

    private IngestionContext context() {
        SourceRepository repository = SourceRepository.create(
                SourceType.GITHUB, "Handbook", "acme", "handbook", null, "main", "docs");
        IngestionRun run = IngestionRun.start(
                UUID.randomUUID(), SourceSyncRequestedEvent.Trigger.MANUAL, "rev-1");
        SourceFile file = new SourceFile("docs/a.md", "# A\n\nBody.", "rev-1", "blob-1");
        return new IngestionContext(run, repository, file, "hash-1");
    }
}
