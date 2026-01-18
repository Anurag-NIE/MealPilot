package com.mealpilot.api.error;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(
    name = "ApiErrorResponse",
    description = "Standard JSON error envelope returned for all API errors",
    example = "{\n"
        + "  \"timestamp\": \"2026-01-17T00:00:00Z\",\n"
        + "  \"status\": 400,\n"
        + "  \"error\": \"Bad Request\",\n"
        + "  \"message\": \"Validation failed\",\n"
        + "  \"path\": \"/api/items\",\n"
        + "  \"requestId\": \"d7b0c3a1-...\",\n"
        + "  \"fieldErrors\": [{\"field\": \"name\", \"message\": \"name is required\"}]\n"
        + "}"
)
public record ApiErrorResponse(
    @Schema(description = "Server timestamp", example = "2026-01-17T00:00:00Z")
    Instant timestamp,

    @Schema(description = "HTTP status code", example = "400")
    int status,

    @Schema(description = "HTTP reason phrase", example = "Bad Request")
    String error,

    @Schema(description = "Human-readable error message", example = "Validation failed")
    String message,

    @Schema(description = "Request path", example = "/api/items")
    String path,

    @Schema(description = "Request correlation id", example = "d7b0c3a1-...")
    String requestId,

    @Schema(description = "Field-level validation errors (when applicable)")
    List<ApiFieldError> fieldErrors) {
}
