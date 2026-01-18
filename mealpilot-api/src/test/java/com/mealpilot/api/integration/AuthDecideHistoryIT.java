package com.mealpilot.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealpilot.api.auth.UserAccountRepository;
import com.mealpilot.api.decide.DecisionRepository;
import com.mealpilot.api.items.ItemRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
    // Keep integration tests deterministic and fast.
    "mealpilot.audit.enabled=false",
    "mealpilot.ratelimit.enabled=false"
})
class AuthDecideHistoryIT {

  @Autowired
  private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

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
  void auth_decide_history_cursorPaging() {
    String username = "it_user_" + Instant.now().toEpochMilli();
    String password = "Password123!";

    // register
    webTestClient.post()
        .uri("/api/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").exists()
        .jsonPath("$.username").isEqualTo(username);

    // login
    EntityExchangeResult<byte[]> login = webTestClient.post()
        .uri("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .returnResult();

    String token = readJson(login.getResponseBody()).path("accessToken").asText(null);
    assertThat(token).isNotBlank();

    // create a couple items
    webTestClient.post()
        .uri("/api/items")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"name\":\"Chicken Biryani\",\"tags\":[\"comfort\"],\"priceEstimate\":199}")
        .exchange()
        .expectStatus().isCreated();

    webTestClient.post()
        .uri("/api/items")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"name\":\"Tacos\",\"tags\":[\"spicy\"],\"priceEstimate\":149}")
        .exchange()
        .expectStatus().isCreated();

    // decide twice (creates decision history entries)
    webTestClient.post()
        .uri("/api/decide")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"budget\":250,\"mustHaveTags\":[\"spicy\"],\"avoidTags\":[],\"query\":\"\",\"limit\":3}")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
                .jsonPath("$.decisionId").exists();

    webTestClient.post()
        .uri("/api/decide")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"budget\":250,\"mustHaveTags\":[\"spicy\"],\"avoidTags\":[],\"query\":\"\",\"limit\":3}")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
                .jsonPath("$.decisionId").exists();

    // list decisions with limit=1 -> should produce a next cursor
        EntityExchangeResult<byte[]> page1 = webTestClient.get()
        .uri(uriBuilder -> uriBuilder.path("/api/decisions").queryParam("limit", 1).build())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().exists("X-Next-Cursor")
                .expectBody()
        .returnResult();

    String cursor = page1.getResponseHeaders().getFirst("X-Next-Cursor");
    assertThat(cursor).isNotBlank();

        JsonNode page1Body = readJson(page1.getResponseBody());
        assertThat(page1Body.isArray()).isTrue();
        assertThat(page1Body.size()).isEqualTo(1);
        assertThat(page1Body.get(0).path("id").asText()).isNotBlank();

    // fetch next page using cursor
        EntityExchangeResult<byte[]> page2 = webTestClient.get()
        .uri(uriBuilder -> uriBuilder.path("/api/decisions")
            .queryParam("limit", 10)
            .queryParam("cursor", cursor)
            .build())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange()
        .expectStatus().isOk()
                .expectBody()
                .returnResult();

        JsonNode page2Body = readJson(page2.getResponseBody());
        assertThat(page2Body.isArray()).isTrue();
        assertThat(page2Body.size()).isGreaterThanOrEqualTo(1);
  }

    private JsonNode readJson(byte[] bytes) {
        try {
            return objectMapper.readTree(bytes);
        } catch (Exception e) {
            throw new AssertionError("Failed to parse JSON response", e);
        }
    }
}
