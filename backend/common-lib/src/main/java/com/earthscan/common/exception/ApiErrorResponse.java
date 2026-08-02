package com.earthscan.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

/**
 * The single error shape returned by every endpoint in every service.
 *
 * <p>{@code message} is kept as a top-level string because the existing React client already reads
 * {@code error.response.data.message}; the extra fields are additive, so no frontend code breaks.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiError", description = "Uniform error payload returned by all EarthScan services")
public class ApiErrorResponse {

    @Schema(example = "2026-07-28T09:15:30.123Z")
    private Instant timestamp;

    @Schema(example = "400")
    private int status;

    @Schema(example = "Bad Request")
    private String error;

    @Schema(description = "Human readable summary, safe to show to the end user",
            example = "Email is already registered")
    private String message;

    @Schema(example = "/api/auth/register")
    private String path;

    @Schema(description = "Correlation id, also returned in the X-Correlation-Id response header")
    private String correlationId;

    @Schema(description = "Field-level validation failures, keyed by property name")
    private Map<String, List<String>> fieldErrors;

    public ApiErrorResponse() {
    }

    public static ApiErrorResponse of(HttpStatus status, String message, String path) {
        ApiErrorResponse response = new ApiErrorResponse();
        response.timestamp = Instant.now();
        response.status = status.value();
        response.error = status.getReasonPhrase();
        response.message = message;
        response.path = path;
        response.correlationId = MDC.get("correlationId");
        return response;
    }

    public static ApiErrorResponse validation(String message,
                                              String path,
                                              Map<String, List<String>> fieldErrors) {
        ApiErrorResponse response = of(HttpStatus.BAD_REQUEST, message, path);
        response.fieldErrors = fieldErrors;
        return response;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Map<String, List<String>> getFieldErrors() {
        return fieldErrors;
    }

    public void setFieldErrors(Map<String, List<String>> fieldErrors) {
        this.fieldErrors = fieldErrors;
    }
}
