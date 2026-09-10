package com.knowledge.platform.source.integration.adapter;

import com.knowledge.platform.source.model.dto.BinaryAsset;
import com.knowledge.platform.source.model.dto.RepositoryDescriptor;
import com.knowledge.platform.source.model.entity.SourceType;
import com.knowledge.platform.source.service.SourceProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Shared HTTP plumbing for REST-based provider adapters.
 *
 * <p>Holds only what is genuinely common: a timeout-bounded {@link RestClient}, translation of
 * transport failures into {@link SourceAdapterException}, and a constant-time secret comparison.
 * Anything a provider does differently -- and almost everything is -- stays in its own adapter, so
 * this base class never becomes the place provider quirks accumulate.
 */
@Slf4j
public abstract class AbstractRestSourceAdapter implements SourceAdapter {

    protected final SourceProperties properties;

    protected AbstractRestSourceAdapter(SourceProperties properties) {
        this.properties = properties;
    }

    /**
     * Builds a client for one repository.
     *
     * <p>Per-call rather than a shared field because the base URL and the credential differ per
     * connected repository, and a shared client would either leak one repository's token to another
     * or need a request-scoped interceptor to avoid it.
     */
    protected RestClient clientFor(String baseUrl, Consumer<HttpHeaders> headers) {
        Duration timeout = properties.requestTimeout();
        // The JDK client directly rather than Boot's ClientHttpRequestFactoryBuilder: that builder
        // lives in spring-boot-http-client, and a library module should not acquire a Boot dependency
        // just to set two timeouts. Both bounds are set explicitly -- an unbounded read timeout is how
        // a single unresponsive provider ends up holding a job thread indefinitely.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory((ClientHttpRequestFactory) requestFactory)
                .defaultHeaders(headers)
                .build();
    }

    /**
     * Runs a provider call, translating anything that goes wrong.
     *
     * @param description what was being attempted, for the exception message. Must never contain a
     *     credential.
     */
    protected <T> T call(SourceType sourceType, String description, ProviderCall<T> call) {
        try {
            return call.execute();
        } catch (HttpClientErrorException e) {
            throw new SourceAdapterException(sourceType,
                    description + " failed: " + e.getStatusCode(), isRetryable(e.getStatusCode()), e);
        } catch (HttpServerErrorException e) {
            throw new SourceAdapterException(sourceType,
                    description + " failed: " + e.getStatusCode(), true, e);
        } catch (RestClientException e) {
            // Transport-level: connect timeout, DNS, TLS. Always worth another attempt.
            throw new SourceAdapterException(sourceType, description + " failed", true, e);
        }
    }

    /**
     * 429 and 5xx are transient. 401/403/404 are not: the token is wrong, the repository is gone, or
     * the path does not exist, and retrying changes none of those.
     */
    private boolean isRetryable(HttpStatusCode status) {
        return status.value() == 429 || status.is5xxServerError();
    }

    /**
     * Compares two secrets without leaking their contents through timing.
     *
     * <p>{@code String.equals} returns on the first differing character, so an attacker can recover a
     * signature byte by byte from response latency. {@code MessageDigest.isEqual} is the constant-time
     * comparison in the JDK.
     */
    protected boolean secretsMatch(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }

    protected void logProviderCall(RepositoryDescriptor descriptor, String operation) {
        // descriptor.toString() is overridden to exclude the token.
        log.debug("{} on {}", operation, descriptor);
    }

    /** A provider call that may throw a Spring REST client exception. */
    @FunctionalInterface
    protected interface ProviderCall<T> {
        T execute();
    }

    /**
     * Fetches bytes from a provider endpoint that serves raw file content.
     *
     * <p>Shared because the differences between providers here are the URL and the headers, which
     * each adapter supplies; the rest -- capping the size, working out a content type, treating a
     * 404 as absence rather than failure -- is identical and should not be written three times.
     *
     * <p>The size cap is enforced on what arrived rather than on a length header, because a header
     * is a claim by the other end and this one decides how much memory to hold.
     */
    protected Optional<BinaryAsset> fetchBinary(
            SourceType sourceType, String description, String path, BinaryCall call) {
        ResponseEntity<byte[]> response;
        try {
            response = call(sourceType, description, call::execute);
        } catch (SourceAdapterException e) {
            if (isNotFound(e)) {
                return Optional.empty();
            }
            throw e;
        }
        if (response == null || response.getBody() == null || response.getBody().length == 0) {
            return Optional.empty();
        }
        byte[] body = response.getBody();
        if (body.length > properties.maxFileBytes()) {
            throw new SourceAdapterException(sourceType,
                    description + " exceeded the " + properties.maxFileBytes() + " byte limit",
                    false, null);
        }
        MediaType reported = response.getHeaders().getContentType();
        return Optional.of(new BinaryAsset(body, contentTypeOf(reported, path), path));
    }

    /**
     * The content type to serve.
     *
     * <p>The provider's own answer is used only when it is already an image type. Otherwise the
     * extension decides, because what providers actually return here is rarely useful: GitHub echoes
     * the vendor media type it was asked for ({@code application/vnd.github.raw}), and others answer
     * {@code application/octet-stream} for everything. Trusting either would have this reject a PNG
     * as "not an image", which is precisely what it did.
     *
     * <p>Guessing is bounded to a known list. An unrecognised extension stays octet-stream and the
     * caller refuses to serve it, so an unexpected file cannot acquire an image type by accident.
     */
    private String contentTypeOf(MediaType reported, String path) {
        if (reported != null && "image".equalsIgnoreCase(reported.getType())) {
            return reported.toString();
        }
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        String extension = dot < 0 ? "" : lower.substring(dot + 1);
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "avif" -> "image/avif";
            case "svg" -> "image/svg+xml";
            case "ico" -> "image/x-icon";
            case "bmp" -> "image/bmp";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }

    /**
     * Whether a provider failure was simply "no such thing".
     *
     * <p>Shared rather than repeated: it was identical in all three adapters, and a fourth copy for
     * binary reads would have been the point at which they started drifting apart.
     */
    protected boolean isNotFound(SourceAdapterException e) {
        return e.getCause() instanceof HttpClientErrorException clientError
                && clientError.getStatusCode() == HttpStatus.NOT_FOUND;
    }

    /** A provider call returning a raw response, so headers are available alongside the body. */
    @FunctionalInterface
    protected interface BinaryCall {
        ResponseEntity<byte[]> execute();
    }
}
