package com.mealpilot.api.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

@Configuration
public class SecurityConfig {

  @Bean
  @Order(1)
  SecurityWebFilterChain publicWebFilterChain(ServerHttpSecurity http) {
    return http
      // Public endpoints must NOT run through the JWT resource-server filter chain.
      // Otherwise a stale/invalid Authorization: Bearer token can break login/register.
      .securityMatcher(ServerWebExchangeMatchers.pathMatchers(
        "/",
        "/api/health",
        "/api/auth/**",
        "/v3/api-docs",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/actuator/health",
        "/actuator/info"))
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(Customizer.withDefaults())
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .logout(ServerHttpSecurity.LogoutSpec::disable)
        .headers(headers -> headers
            .contentTypeOptions(Customizer.withDefaults())
            .frameOptions(frame -> frame.mode(org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
            .referrerPolicy(ref -> ref.policy(ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.NO_REFERRER))
            .permissionsPolicy(pp -> pp.policy("geolocation=(), microphone=(), camera=()"))
        )
        .authorizeExchange(ex -> ex.anyExchange().permitAll())
        .build();
  }

  @Bean
  @Order(2)
  SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    return http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
      .cors(Customizer.withDefaults())
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .logout(ServerHttpSecurity.LogoutSpec::disable)
        .headers(headers -> headers
            .contentTypeOptions(Customizer.withDefaults())
            .frameOptions(frame -> frame.mode(org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
            .referrerPolicy(ref -> ref.policy(ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.NO_REFERRER))
            .permissionsPolicy(pp -> pp.policy("geolocation=(), microphone=(), camera=()"))
        )
        .authorizeExchange(ex -> ex
        .pathMatchers(HttpMethod.OPTIONS).permitAll()
          .pathMatchers("/").permitAll()
            .pathMatchers("/api/health").permitAll()
            .pathMatchers("/api/auth/**").permitAll()
            .pathMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
            .pathMatchers("/actuator/health", "/actuator/info").permitAll()
            .anyExchange().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecretKey jwtSecretKey(@Value("${mealpilot.jwt.secret:dev-secret-change-me-please-32-bytes-min}") String secret) {
    // HS256 needs a reasonably long secret; enforce to avoid weak tokens in dev.
    if (secret == null || secret.length() < 32) {
      throw new IllegalStateException("mealpilot.jwt.secret must be at least 32 characters");
    }
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  ReactiveJwtDecoder reactiveJwtDecoder(SecretKey jwtSecretKey) {
    return NimbusReactiveJwtDecoder.withSecretKey(jwtSecretKey)
        .macAlgorithm(MacAlgorithm.HS256)
        .build();
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
    byte[] secret = jwtSecretKey.getEncoded();
    if (secret == null || secret.length == 0) {
      throw new IllegalStateException("JWT secret key bytes are missing");
    }
    return new NimbusJwtEncoder(new ImmutableSecret<>(secret));
  }
}