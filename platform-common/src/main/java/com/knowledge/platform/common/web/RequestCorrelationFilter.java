package com.knowledge.platform.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id, and puts it where the logs can see it.
 *
 * <p>This exists because the frontend renders on the server. When a page fails, the browser shows a
 * rendered error and nothing else -- no failed request in the network tab, no status, no stack. The
 * only evidence is a line in a backend log that nothing connects to the page the reader was looking
 * at. Correlating them meant matching timestamps by hand.
 *
 * <p>The frontend generates the id and sends it; this filter adopts it, so one identifier spans the
 * browser request, the server render, and every backend call made during it. It is echoed back in
 * the response and included in error payloads, so an id shown on screen is greppable directly.
 *
 * <p>Runs before everything, including security. An authentication failure is exactly the kind of
 * error worth tracing, and a filter ordered after the security chain would not see it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    /** Referenced by the logging pattern; renaming it silently empties every log line. */
    public static final String MDC_KEY = "requestId";

    /** Long enough to be unique in practice, short enough to read aloud from a screen. */
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String requestId = sanitise(request.getHeader(HEADER));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Threads are pooled. Leaving the id behind would stamp it on the next unrelated
            // request, which is worse than having no id at all -- a wrong trail costs more than a
            // missing one.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Accepts a caller-supplied id only if it is safe to write into a log line.
     *
     * <p>An inbound header that reaches a log unfiltered is a log injection: newlines let a caller
     * forge entries, and unbounded length lets them flood the file. Restricting to a short run of
     * URL-safe characters keeps every id greppable and makes a hostile one simply ignored rather
     * than rejected -- a malformed id is not worth failing a request over.
     */
    private String sanitise(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_LENGTH) {
            return null;
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            boolean allowed = Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.';
            if (!allowed) {
                return null;
            }
        }
        return candidate;
    }
}
