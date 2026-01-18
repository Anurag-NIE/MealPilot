package com.mealpilot.api.error;

import com.mealpilot.api.config.SecurityConfig;
import com.mealpilot.api.health.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = HealthController.class)
@Import({SecurityConfig.class, ApiErrorWebExceptionHandler.class})
class ErrorEnvelopeTest {

  @Autowired
  private WebTestClient webTestClient;

  @Test
  void notFound_isJsonEnvelope() {
    webTestClient.get()
        .uri("/api/auth/does-not-exist")
        .exchange()
        .expectStatus().isNotFound()
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.status").isEqualTo(404)
        .jsonPath("$.error").isEqualTo("Not Found")
        .jsonPath("$.path").isEqualTo("/api/auth/does-not-exist")
        .jsonPath("$.timestamp").exists()
        .jsonPath("$.requestId").exists();
  }

  @Test
  void methodNotAllowed_isJsonEnvelope() {
    webTestClient.post()
        .uri("/api/health")
        .exchange()
        .expectStatus().isEqualTo(405)
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.status").isEqualTo(405)
        .jsonPath("$.error").isEqualTo("Method Not Allowed")
        .jsonPath("$.path").isEqualTo("/api/health");
  }
}
