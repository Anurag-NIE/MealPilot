package com.mealpilot.api.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class ApiErrorWebExceptionHandler implements WebExceptionHandler {

  private final ObjectMapper objectMapper;

  public ApiErrorWebExceptionHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    if (exchange.getResponse().isCommitted()) {
      return Mono.error(ex);
    }

    HttpStatus status = resolveStatus(ex);

    ApiErrorResponse body = new ApiErrorResponse(
        Instant.now(),
        status.value(),
        status.getReasonPhrase(),
        resolveMessage(ex, status),
        exchange.getRequest().getPath().value(),
        exchange.getRequest().getId(),
        resolveFieldErrors(ex)
    );

    byte[] bytes;
    try {
      bytes = objectMapper.writeValueAsBytes(body);
    } catch (JsonProcessingException jsonEx) {
      // Last-ditch fallback that is still JSON.
      String fallback = "{\"status\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase()
          + "\",\"message\":\"Unexpected error\"}";
      bytes = fallback.getBytes(StandardCharsets.UTF_8);
    }

    // Use raw status code to avoid falling back to 200 in some handler chains.
    exchange.getResponse().setRawStatusCode(status.value());
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    exchange.getResponse().getHeaders().remove(HttpHeaders.CONTENT_LENGTH);

    return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
  }

  private static HttpStatus resolveStatus(Throwable ex) {
    if (ex instanceof ResponseStatusException rse) {
      HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
      return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
    }
    if (ex instanceof WebExchangeBindException) {
      return HttpStatus.BAD_REQUEST;
    }
    if (ex instanceof ServerWebInputException) {
      return HttpStatus.BAD_REQUEST;
    }
    if (ex instanceof MethodNotAllowedException) {
      return HttpStatus.METHOD_NOT_ALLOWED;
    }
    if (ex instanceof AccessDeniedException) {
      return HttpStatus.FORBIDDEN;
    }

    return HttpStatus.INTERNAL_SERVER_ERROR;
  }

  private static String resolveMessage(Throwable ex, HttpStatus status) {
    if (ex instanceof MethodNotAllowedException) {
      return status.getReasonPhrase();
    }

    if (ex instanceof WebExchangeBindException) {
      return "Validation failed";
    }

    if (ex instanceof ServerWebInputException swe) {
      if (swe.getReason() != null && !swe.getReason().isBlank()) {
        // WebFlux often reports body validation issues as "Validation failure".
        // Normalize to one stable message.
        if (swe.getReason().toLowerCase().contains("validation")) {
          return "Validation failed";
        }
        return swe.getReason();
      }
      return "Invalid request";
    }

    if (ex instanceof ResponseStatusException rse) {
      if (rse.getReason() != null && !rse.getReason().isBlank()) {
        return rse.getReason();
      }
      return status.getReasonPhrase();
    }

    String message = ex.getMessage();
    if (message == null || message.isBlank()) {
      return status.getReasonPhrase();
    }

    // Avoid leaking verbose internal details.
    return message;
  }

  private static List<ApiFieldError> resolveFieldErrors(Throwable ex) {
    if (ex instanceof WebExchangeBindException bindEx) {
      return bindEx.getFieldErrors().stream()
          .filter(Objects::nonNull)
          .map(ApiErrorWebExceptionHandler::mapFieldError)
          .toList();
    }

    if (ex instanceof ServerWebInputException swe && swe.getCause() instanceof WebExchangeBindException bindEx) {
      return bindEx.getFieldErrors().stream()
          .filter(Objects::nonNull)
          .map(ApiErrorWebExceptionHandler::mapFieldError)
          .toList();
    }
    return null;
  }

  private static ApiFieldError mapFieldError(FieldError fieldError) {
    String message = fieldError.getDefaultMessage();
    if (message == null || message.isBlank()) {
      message = "Invalid value";
    }
    return new ApiFieldError(fieldError.getField(), message);
  }
}
