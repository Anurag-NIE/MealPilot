package com.mealpilot.api.config.filter;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RateLimitFilter implements WebFilter {

  private final boolean enabled;
  private final int defaultPerMinute;
  private final int authPerMinute;

  private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

  public RateLimitFilter(
      @Value("${mealpilot.ratelimit.enabled:true}") boolean enabled,
      @Value("${mealpilot.ratelimit.default.per-minute:240}") int defaultPerMinute,
      @Value("${mealpilot.ratelimit.auth.per-minute:30}") int authPerMinute
  ) {
    this.enabled = enabled;
    this.defaultPerMinute = Math.max(1, defaultPerMinute);
    this.authPerMinute = Math.max(1, authPerMinute);
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!enabled) {
      return chain.filter(exchange);
    }

    String path = exchange.getRequest().getPath().value();
    HttpMethod method = exchange.getRequest().getMethod();

    // Never rate limit preflight, health, or docs.
    if (method == HttpMethod.OPTIONS
        || path.equals("/")
        || path.startsWith("/api/health")
        || path.startsWith("/actuator/health")
        || path.startsWith("/v3/api-docs")
        || path.startsWith("/swagger-ui")) {
      return chain.filter(exchange);
    }

    int limit = path.startsWith("/api/auth/") ? authPerMinute : defaultPerMinute;

    String ip = resolveClientIp(exchange);
    String key = (path.startsWith("/api/auth/") ? "auth:" : "api:") + ip;

    if (!allow(key, limit, Duration.ofMinutes(1))) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests");
    }

    return chain.filter(exchange);
  }

  private boolean allow(String key, int limit, Duration window) {
    long nowEpochSeconds = Instant.now().getEpochSecond();
    long windowSeconds = window.getSeconds();

    WindowCounter counter = counters.compute(key, (k, existing) -> {
      if (existing == null) {
        return new WindowCounter(nowEpochSeconds, new AtomicInteger(1));
      }

      long age = nowEpochSeconds - existing.windowStartEpochSeconds;
      if (age >= windowSeconds) {
        existing.windowStartEpochSeconds = nowEpochSeconds;
        existing.count.set(1);
        return existing;
      }

      existing.count.incrementAndGet();
      return existing;
    });

    return counter.count.get() <= limit;
  }

  private static String resolveClientIp(ServerWebExchange exchange) {
    // If behind a trusted proxy, wire a real ForwardedHeaderTransformer.
    String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      String first = xff.split(",")[0].trim();
      if (!first.isBlank()) return first;
    }

    if (exchange.getRequest().getRemoteAddress() != null
        && exchange.getRequest().getRemoteAddress().getAddress() != null) {
      return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }

    return "unknown";
  }

  private static final class WindowCounter {
    private volatile long windowStartEpochSeconds;
    private final AtomicInteger count;

    private WindowCounter(long windowStartEpochSeconds, AtomicInteger count) {
      this.windowStartEpochSeconds = windowStartEpochSeconds;
      this.count = count;
    }
  }
}
