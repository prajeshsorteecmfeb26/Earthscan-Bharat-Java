package com.earthscan.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;

/**
 * Uniform success envelope for every endpoint across every service.
 *
 * <p><strong>Why an envelope at all, and why an opt-in one.</strong> A wrapper gives clients one
 * shape to parse, a place to carry the correlation id, and a natural home for pagination metadata.
 * The cost is that it breaks any client written against the raw payload — which, for this platform,
 * is the entire existing React application.</p>
 *
 * <p>So the envelope is applied deliberately rather than globally: new endpoints and the paginated
 * collection endpoints return {@code ApiResponse<T>}, while the endpoints the current frontend
 * already consumes keep their original shape. {@link #getData()} always holds the payload the
 * un-enveloped endpoint would have returned, so a client can migrate one call at a time instead of
 * in a single breaking release. See {@code ApiResponseAdvice} for the mechanism.</p>
 *
 * @param <T> the payload type
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiResponse", description = "Uniform success envelope returned by EarthScan services")
public class ApiResponse<T> {

    @Schema(description = "Always true on this type; failures are returned as ApiError instead",
            example = "true")
    private boolean success;

    @Schema(description = "Short, human-readable outcome, safe to display",
            example = "Listing created successfully")
    private String message;

    @Schema(description = "The payload. Identical to what the un-enveloped endpoint would return.")
    private T data;

    @Schema(description = "Pagination metadata. Present only for paged collection responses.")
    private PageMetadata page;

    @Schema(description = "Correlation id, also present in the X-Correlation-Id response header")
    private String correlationId;

    @Schema(example = "2026-07-29T04:15:30.123Z")
    private Instant timestamp;

    // ------------------------------------------------------------------ factories

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .correlationId(MDC.get("correlationId"))
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .correlationId(MDC.get("correlationId"))
                .timestamp(Instant.now())
                .build();
    }

    /** Acknowledgement with no payload — used for deletes and state transitions. */
    public static ApiResponse<Void> message(String message) {
        return ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .correlationId(MDC.get("correlationId"))
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Flattens a Spring {@link Page} into a list plus separate metadata.
     *
     * <p>Spring's own {@code Page} serialisation is avoided on the wire deliberately: its JSON shape
     * is an implementation detail of Spring Data that has changed between versions, and Boot 3.3
     * logs a warning about serialising {@code PageImpl} directly for exactly that reason. Mapping to
     * an explicit {@link PageMetadata} makes the contract ours rather than the framework's.</p>
     */
    public static <T> ApiResponse<List<T>> page(Page<T> page) {
        return ApiResponse.<List<T>>builder()
                .success(true)
                .data(page.getContent())
                .page(PageMetadata.from(page))
                .correlationId(MDC.get("correlationId"))
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<List<T>> page(Page<T> page, String message) {
        ApiResponse<List<T>> response = page(page);
        response.setMessage(message);
        return response;
    }

    /**
     * Pagination metadata, kept as an explicit contract rather than Spring's {@code Page} JSON.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Schema(name = "PageMetadata")
    public static class PageMetadata {

        @Schema(description = "Zero-based page index", example = "0")
        private int number;

        @Schema(example = "12")
        private int size;

        @Schema(description = "Elements on this page", example = "12")
        private int numberOfElements;

        @Schema(description = "Total elements across all pages", example = "137")
        private long totalElements;

        @Schema(example = "12")
        private int totalPages;

        private boolean first;
        private boolean last;
        private boolean empty;

        @Schema(description = "Applied sort orders, e.g. [\"landIntelligenceScore: DESC\"]")
        private List<String> sort;

        static <T> PageMetadata from(Page<T> page) {
            return PageMetadata.builder()
                    .number(page.getNumber())
                    .size(page.getSize())
                    .numberOfElements(page.getNumberOfElements())
                    .totalElements(page.getTotalElements())
                    .totalPages(page.getTotalPages())
                    .first(page.isFirst())
                    .last(page.isLast())
                    .empty(page.isEmpty())
                    .sort(page.getSort().stream()
                            .map(order -> order.getProperty() + ": " + order.getDirection())
                            .toList())
                    .build();
        }
    }

    /**
     * Convenience for tests and clients: exposes the payload as a map when it is one.
     *
     * @return the data cast to a map, or {@code null} when the payload is not a map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> dataAsMap() {
        return data instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }
}
