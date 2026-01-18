package com.mealpilot.api.config.filter;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Signal;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuditLogFilter implements WebFilter {

  private static final Logger log = LoggerFactory.getLogger("audit");

  private final boolean enabled;

  public AuditLogFilter(@Value("${mealpilot.audit.enabled:true}") boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!enabled) {
      return chain.filter(exchange);
    }

    String method = String.valueOf(exchange.getRequest().getMethod());
    String path = exchange.getRequest().getPath().value();

    // Skip very noisy endpoints.
    if (path.startsWith("/actuator/health") || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")) {
      return chain.filter(exchange);
    }

    Instant start = Instant.now();

    Mono<AuditUser> userMono = exchange.getPrincipal()
        .ofType(Authentication.class)
        .map(AuditUser::from)
        .defaultIfEmpty(AuditUser.anonymous());

    return chain.filter(exchange)
        .materialize()
        .flatMap(signal -> writeAuditLog(exchange, signal, userMono, start, method, path).thenReturn(signal))
        .dematerialize();
  }

  private Mono<Void> writeAuditLog(
      ServerWebExchange exchange,
      Signal<Void> signal,
      Mono<AuditUser> userMono,
      Instant start,
      String method,
      String path
  ) {
    int status = Optional.ofNullable(exchange.getResponse().getStatusCode())
      .map(sc -> sc.value())
        .orElseGet(() -> signal.isOnError() ? 500 : 200);

    long ms = Duration.between(start, Instant.now()).toMillis();

    String requestId = (String) exchange.getAttributes().get(RequestIdFilter.ATTR);
    if (requestId == null || requestId.isBlank()) {
      requestId = exchange.getRequest().getId();
    }

    String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
    if (ip == null || ip.isBlank()) {
      ip = exchange.getRequest().getRemoteAddress() != null
          && exchange.getRequest().getRemoteAddress().getAddress() != null
          ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
          : "unknown";
    } else {
      ip = ip.split(",")[0].trim();
    }

    final String requestIdFinal = requestId;
    final String ipFinal = ip;

    return userMono
      .doOnNext(u -> log.info(
        "requestId={} ip={} user={} roles={} method={} path={} status={} ms={}",
        requestIdFinal, ipFinal, u.user(), u.roles(), method, path, status, ms))
      .then();
  }

  private record AuditUser(String user, String roles) {
    static AuditUser anonymous() {
      return new AuditUser("-", "-");
    }

    static AuditUser from(Authentication a) {
      String user = a.getName();
      if (a instanceof JwtAuthenticationToken jat) {
        user = jat.getToken().getSubject();
      }
      return new AuditUser(user, String.valueOf(a.getAuthorities()));
    }
  }
}
