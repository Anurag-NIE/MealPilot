package com.mealpilot.api.health;

import com.mealpilot.api.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = HealthController.class)
@Import(SecurityConfig.class)
class HealthControllerTest {

  @Autowired
  private WebTestClient webTestClient;

  @Test
  void health_returnsOkPayload() {
    webTestClient.get()
        .uri("/api/health")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.status").isEqualTo("ok")
        .jsonPath("$.service").isEqualTo("mealpilot-api")
        .jsonPath("$.time").exists();
  }
}
