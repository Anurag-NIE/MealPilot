package com.mealpilot.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealpilot.api.auth.UserAccountRepository;
import com.mealpilot.api.decide.DecisionRepository;
import com.mealpilot.api.items.ItemRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
    "mealpilot.audit.enabled=false",

    // Enable rate limiting and make it easy to trigger.
    // Note: /api/auth/** is the strict bucket.
    "mealpilot.ratelimit.enabled=true",
    "mealpilot.ratelimit.auth.per-minute=2",
    "mealpilot.ratelimit.default.per-minute=1000"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RateLimitAuthIT {

  @Autowired
  private WebTestClient webTestClient;

  @Autowired
  private UserAccountRepository userAccountRepository;

  @Autowired
  private ItemRepository itemRepository;

  @Autowired
  private DecisionRepository decisionRepository;

  @BeforeEach
  void cleanup() {
    Mono.when(
        decisionRepository.deleteAll(),
        itemRepository.deleteAll(),
        userAccountRepository.deleteAll()
    ).block();
  }

  @Test
  void authRateLimit_exceedsWithinWindow_returns429() {
    String username = "rl_user_" + Instant.now().toEpochMilli();
    String password = "Password123!";

    // 1st auth request (register) -> ok
    webTestClient.post()
        .uri("/api/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
        .exchange()
        .expectStatus().isCreated();

    // 2nd auth request (login) -> ok
    webTestClient.post()
        .uri("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
        .exchange()
        .expectStatus().isOk();

    // 3rd auth request (login again) -> should be rate limited
    EntityExchangeResult<byte[]> res = webTestClient.post()
        .uri("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
        .exchange()
        .expectStatus().isEqualTo(429)
        .expectBody()
        .returnResult();

    // If ApiErrorWebExceptionHandler wraps it, we should see a readable message.
    String body = res.getResponseBody() == null ? "" : new String(res.getResponseBody());
    assertThat(body).containsIgnoringCase("too many");
  }
}
