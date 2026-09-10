package com.knowledge.platform.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The filter decides what goes into a log line, which makes it a security control.
 *
 * <p>A caller-supplied header written to a log unfiltered lets that caller forge log entries by
 * embedding newlines -- so an attacker can write whatever they like into the record of what
 * happened. These tests are about what the filter refuses.
 */
class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    @DisplayName("a well-formed caller id is adopted, so one id spans both sides")
    void adoptsACallerSuppliedId() throws Exception {
        String seen = runWith("abc-123_XYZ.4");

        assertThat(seen).isEqualTo("abc-123_XYZ.4");
    }

    @Test
    @DisplayName("a newline cannot be smuggled into the logs")
    void rejectsNewlines() throws Exception {
        String seen = runWith("good-id\nERROR forged log line");

        // Replaced rather than rejected: a hostile id is not worth failing a request over, and the
        // request still needs an id to be traceable.
        assertThat(seen).doesNotContain("forged").hasSize(36);
    }

    @Test
    @DisplayName("an overlong id is replaced rather than flooding every line")
    void rejectsOverlongIds() throws Exception {
        String seen = runWith("x".repeat(500));

        assertThat(seen).hasSize(36);
    }

    @Test
    @DisplayName("no id supplied means one is generated")
    void generatesWhenAbsent() throws Exception {
        String seen = runWith(null);

        assertThat(seen).hasSize(36);
    }

    @Test
    @DisplayName("the id is cleared afterwards, so a pooled thread does not carry it onward")
    void clearsTheContextAfterwards() throws Exception {
        runWith("abc-123");

        // A stale id stamps the next unrelated request with the wrong trail, which costs more than
        // having no trail at all.
        assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).isNull();
    }

    /** Runs one request through the filter and reports the id observed inside the chain. */
    private String runWith(String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/articles");
        if (header != null) {
            request.addHeader(RequestCorrelationFilter.HEADER, header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] observed = new String[1];
        FilterChain chain = (req, res) -> observed[0] = MDC.get(RequestCorrelationFilter.MDC_KEY);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestCorrelationFilter.HEADER)).isEqualTo(observed[0]);
        return observed[0];
    }
}
