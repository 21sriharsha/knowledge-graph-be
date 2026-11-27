package com.knowledge.platform.common.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledge.platform.common.model.ApiError;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Caller mistakes are client errors, not 500s (bug fix D3, D5). */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    static class Article {
        String title;
    }

    @Test
    @DisplayName("AC-6 an unknown sort property is a 400 INVALID_REQUEST naming the property")
    void unknownSortPropertyIsBadRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/studio/articles");
        PropertyReferenceException exception =
                new PropertyReferenceException("nonexistent", TypeInformation.of(Article.class), List.of());

        ResponseEntity<ApiError> response = handler.handleUnknownSortProperty(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getBody().message()).isEqualTo("Unknown sort property 'nonexistent'");
        assertThat(response.getBody().message()).doesNotContain("Article"); // no internal type names
    }

    @Test
    @DisplayName("AC-8 an unmapped path is a 404 NOT_FOUND, not a 500")
    void unknownPathIsNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/studio/nope");
        NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, "/api/studio/nope", "api/studio/nope");

        ResponseEntity<ApiError> response = handler.handleUnknownPath(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().path()).isEqualTo("/api/studio/nope");
    }
}
