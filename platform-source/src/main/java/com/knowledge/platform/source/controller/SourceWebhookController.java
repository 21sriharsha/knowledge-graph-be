package com.knowledge.platform.source.controller;

import com.knowledge.platform.source.model.dto.WebhookOutcome;
import com.knowledge.platform.source.model.dto.WebhookRequest;
import com.knowledge.platform.source.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider webhook endpoints.
 *
 * <p>Anonymous at the HTTP layer by design -- a provider cannot present a session -- and authenticated
 * by signature inside the source module. The endpoint is deliberately per-repository so that each
 * connection has its own secret; one shared secret across every repository would mean one leak
 * compromises them all.
 *
 * <p>The body is taken as a raw {@code String}, not a bound object. Signature verification is computed
 * over exactly the bytes the provider sent, and letting Jackson parse and re-serialize would change
 * whitespace and key order and invalidate every signature.
 *
 * <p>Response codes follow what providers do with them: 202 for accepted, 200 for a verified event we
 * do not act on (anything else makes the provider retry a ping forever), 401 for a failed signature.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "Webhooks", description = "Provider callbacks. Authenticated by signature, not session.")
public class SourceWebhookController {

    private final WebhookService webhookService;

    public SourceWebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/sources/{repositoryId}")
    @Operation(summary = "Accept a provider webhook for one connected repository")
    public ResponseEntity<Map<String, String>> receive(
            @PathVariable UUID repositoryId,
            @RequestBody(required = false) String rawBody,
            HttpServletRequest servletRequest) {

        WebhookOutcome outcome = webhookService.accept(
                repositoryId, new WebhookRequest(headersOf(servletRequest), rawBody == null ? "" : rawBody));

        return switch (outcome.status()) {
            case ACCEPTED -> ResponseEntity.accepted()
                    .body(Map.of("status", "accepted"));
            case IGNORED -> ResponseEntity.ok(
                    Map.of("status", "ignored", "reason", outcome.reason()));
            case REJECTED -> {
                // The reason is logged, not returned: telling an unverified caller why verification
                // failed helps them get it right next time.
                log.warn("Rejected webhook for repository {}: {}", repositoryId, outcome.reason());
                yield ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("status", "rejected"));
            }
        };
    }

    private Map<String, String> headersOf(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Collections.list(request.getHeaderNames())
                .forEach(name -> headers.put(name, request.getHeader(name)));
        return headers;
    }
}
