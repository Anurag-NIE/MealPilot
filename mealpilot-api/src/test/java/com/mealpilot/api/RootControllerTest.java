package com.mealpilot.api;

import com.mealpilot.api.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = RootController.class)
@Import(SecurityConfig.class)
class RootControllerTest {

  @Autowired
  private WebTestClient webTestClient;

  @Test
  void root_isPublicAndReturnsHints() {
    webTestClient.get()
        .uri("/")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.service").isEqualTo("mealpilot-api")
        .jsonPath("$.status").isEqualTo("ok")
        .jsonPath("$.health").isEqualTo("/actuator/health")
        .jsonPath("$.auth.login").isEqualTo("/api/auth/login")
        .jsonPath("$.note").exists();
  }

  @Test
  void root_acceptHtml_returnsLandingPage() {
    webTestClient.get()
        .uri("/")
        .accept(MediaType.TEXT_HTML)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
        .expectBody(String.class)
        .value(body -> {
          org.assertj.core.api.Assertions.assertThat(body).contains("MealPilot API");
          org.assertj.core.api.Assertions.assertThat(body).contains("mealpilot-web");
          org.assertj.core.api.Assertions.assertThat(body).contains("/api/auth/login");
        });
  }
}
