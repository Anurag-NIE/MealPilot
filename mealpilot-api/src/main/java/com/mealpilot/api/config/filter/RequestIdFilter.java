package com.mealpilot.api.config.filter;

import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements WebFilter {

  public static final String HEADER = "X-Request-Id";
  public static final String ATTR = "mealpilot.requestId";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
    String requestId = (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString() : incoming;

    exchange.getAttributes().put(ATTR, requestId);

    exchange.getResponse().getHeaders().set(HEADER, requestId);
    exchange.getResponse().getHeaders().add(HttpHeaders.VARY, HEADER);

    return chain.filter(exchange);
  }
}
