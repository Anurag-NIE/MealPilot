package com.mealpilot.api.error;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiFieldError", description = "A single field validation error")
public record ApiFieldError(
    @Schema(description = "Field name", example = "username")
    String field,

    @Schema(description = "Validation message", example = "username must be at least 3 characters")
    String message) {
}
