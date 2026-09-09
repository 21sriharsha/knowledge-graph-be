package com.knowledge.platform.source.controller;

import com.knowledge.platform.source.delegate.SourceDelegate;
import com.knowledge.platform.source.model.request.CreateSourceRequest;
import com.knowledge.platform.source.model.request.UpdateSourceRequest;
import com.knowledge.platform.source.model.response.SourceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Studio management of connected repositories. Gated on the AUTHOR role by the path prefix. */
@RestController
@RequestMapping("/api/studio/sources")
@Tag(name = "Studio: Sources", description = "Connected external repositories")
public class SourceController {

    private final SourceDelegate sourceDelegate;

    public SourceController(SourceDelegate sourceDelegate) {
        this.sourceDelegate = sourceDelegate;
    }

    @GetMapping
    @Operation(summary = "List connected repositories")
    public List<SourceResponse> list() {
        return sourceDelegate.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one connected repository")
    public SourceResponse get(@PathVariable UUID id) {
        return sourceDelegate.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Connect a repository. Credentials are stored encrypted and never returned.")
    public SourceResponse connect(@Valid @RequestBody CreateSourceRequest request) {
        return sourceDelegate.connect(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a connection. Omitted credentials are left unchanged.")
    public SourceResponse update(
            @PathVariable UUID id, @Valid @RequestBody UpdateSourceRequest request) {
        return sourceDelegate.update(id, request);
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Resume synchronization for a repository")
    public SourceResponse activate(@PathVariable UUID id) {
        return sourceDelegate.setActive(id, true);
    }

    @DeleteMapping("/{id}/activate")
    @Operation(summary = "Stop synchronizing a repository, retaining its content and connection")
    public SourceResponse deactivate(@PathVariable UUID id) {
        return sourceDelegate.setActive(id, false);
    }

    @PostMapping("/{id}/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Request a full re-materialization. Returns immediately; work is deferred.")
    public ResponseEntity<Void> sync(@PathVariable UUID id) {
        sourceDelegate.requestSync(id);
        return ResponseEntity.accepted().build();
    }
}
