package com.mealpilot.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealpilot.api.auth.UserAccountRepository;
import com.mealpilot.api.decide.DecisionRepository;
import com.mealpilot.api.decide.UserPreferenceRepository;
import com.mealpilot.api.items.ItemRepository;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
    "mealpilot.audit.enabled=false",
    "mealpilot.ratelimit.enabled=false"
})
class PreferencesDecideIT {

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

  @Autowired
  private UserPreferenceRepository userPreferenceRepository;

  @BeforeEach
  void clean() {
    decisionRepository.deleteAll().block();
    itemRepository.deleteAll().block();
    userPreferenceRepository.deleteAll().block();
    userAccountRepository.deleteAll().block();
  }

  @Test
  void profileAffectsDecideRankingAndMetaPersists() {
    String username = "prefs_decide_" + System.currentTimeMillis();
    String password = "Password123!";

    // Register + login
    webTestClient.post()
        .uri("/api/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
        .exchange()
        .expectStatus().isCreated();

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

    // Create two items
    webTestClient.post()
        .uri("/api/items")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"name\":\"Veg Salad\",\"restaurantName\":\"Green Place\",\"tags\":[\"veg\"],\"priceEstimate\":900}")
        .exchange()
        .expectStatus().isCreated();

    webTestClient.post()
        .uri("/api/items")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"name\":\"Chicken Wings\",\"restaurantName\":\"Wing Hut\",\"tags\":[\"meat\"],\"priceEstimate\":900}")
        .exchange()
        .expectStatus().isCreated();

    // Upsert profile: prefer veg, avoid meat
    webTestClient.put()
        .uri("/api/preferences/profile")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"preferTags\":[\"veg\"],\"avoidTags\":[\"meat\"]}")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.profile.preferTags[0]").isEqualTo("veg")
        .jsonPath("$.profile.avoidTags[0]").isEqualTo("meat");

    // Decide should prefer the veg item
    EntityExchangeResult<byte[]> decideResult = webTestClient.post()
        .uri("/api/decide")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"limit\":2}")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.candidates[0].item.name").isEqualTo("Veg Salad")
        .jsonPath("$.candidates[0].breakdown.total").exists()
        .returnResult();

    String decisionId = readJson(decideResult.getResponseBody()).path("decisionId").asText(null);
    assertThat(decisionId).isNotBlank();

    // Fetch decision and verify meta persisted
    webTestClient.get()
        .uri("/api/decisions/" + decisionId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.meta.algorithm").isEqualTo("heuristic-score")
        .jsonPath("$.meta.preferenceHash").exists();
  }

  private JsonNode readJson(byte[] bytes) {
    try {
      return objectMapper.readTree(bytes);
    } catch (Exception e) {
      throw new AssertionError("Failed to parse JSON response", e);
    }
  }
}
