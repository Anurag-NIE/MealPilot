package com.mealpilot.api.auth;

import com.mealpilot.api.error.ApiErrorResponse;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiErrorResponse> handle(ResponseStatusException ex, ServerWebExchange exchange) {
    // Let the global handler generate a consistent validation envelope.
    if (ex instanceof ServerWebInputException) {
      throw ex;
    }

    int statusCode = ex.getStatusCode().value();
    HttpStatus status = HttpStatus.resolve(statusCode);
    String error = status != null ? status.getReasonPhrase() : "Error";
    String message = ex.getReason() != null && !ex.getReason().isBlank() ? ex.getReason() : error;

    ApiErrorResponse body = new ApiErrorResponse(
        Instant.now(),
        statusCode,
        error,
        message,
        exchange.getRequest().getPath().value(),
        exchange.getRequest().getId(),
        null
    );

    return ResponseEntity.status(statusCode)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }
}
