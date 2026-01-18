package com.mealpilot.api.auth;

import static org.mockito.Mockito.when;

import com.mealpilot.api.config.SecurityConfig;
import com.mealpilot.api.error.ApiErrorWebExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = AuthController.class)
@Import({SecurityConfig.class, ApiErrorWebExceptionHandler.class})
class AuthControllerTest {

  @Autowired
  private WebTestClient webTestClient;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @MockBean
  private UserAccountRepository userAccountRepository;

  @Test
  void register_createsUser() {
    when(userAccountRepository.findByUsername("anurag")).thenReturn(Mono.empty());

    when(userAccountRepository.save(org.mockito.ArgumentMatchers.any(UserAccount.class)))
        .thenAnswer(inv -> {
          UserAccount u = inv.getArgument(0);
          return Mono.just(new UserAccount("u1", u.username(), u.passwordHash(), u.roles(), u.createdAt()));
        });

    webTestClient.post()
        .uri("/api/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"username\":\"anurag\",\"password\":\"password123\"}")
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").isEqualTo("u1")
      .jsonPath("$.username").isEqualTo("anurag")
      .jsonPath("$.createdAt").exists();
  }

  @Test
  void login_returnsBearerToken() {
    UserAccount user = new UserAccount(
        "u1",
        "anurag",
        passwordEncoder.encode("password123"),
        List.of("ROLE_USER"),
        java.time.Instant.parse("2026-01-01T00:00:00Z")
    );

    when(userAccountRepository.findByUsername("anurag")).thenReturn(Mono.just(user));

    webTestClient.post()
        .uri("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"username\":\"anurag\",\"password\":\"password123\"}")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.accessToken").exists()
        .jsonPath("$.tokenType").isEqualTo("Bearer")
        .jsonPath("$.expiresInSeconds").isNumber();
  }

  @Test
  void register_missingFields_isValidationEnvelope() {
    webTestClient.post()
        .uri("/api/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus().isBadRequest()
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.status").isEqualTo(400)
        .jsonPath("$.error").isEqualTo("Bad Request")
        .jsonPath("$.message").isEqualTo("Validation failed")
        .jsonPath("$.fieldErrors").isArray()
        .jsonPath("$.fieldErrors.length()").isEqualTo(2);
  }
}
