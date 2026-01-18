package com.mealpilot.api.decide;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import com.mealpilot.api.config.SecurityConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = DecisionController.class)
@Import(SecurityConfig.class)
class DecisionControllerTest {

  @Autowired
  private WebTestClient webTestClient;

  @MockBean
  private DecisionRepository decisionRepository;

  @MockBean
  private UserPreferenceRepository userPreferenceRepository;

  @MockBean
  private DecisionHistoryService decisionHistoryService;

  @MockBean
  private DecisionEventRepository decisionEventRepository;

  @Test
  void feedback_requiresStatus() {
    webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("anurag")))
      .post()
        .uri("/api/decisions/dec1/feedback")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  void list_returnsDecisionsForUser() {
    Decision dec1 = new Decision(
      "dec1",
      "anurag",
      Instant.parse("2026-01-02T00:00:00Z"),
      new Decision.DecideInput(250, List.of("comfort"), List.of(), "biryani", 3),
      List.of(),
      null
    );

    Decision dec2 = new Decision(
      "dec2",
      "anurag",
      Instant.parse("2026-01-01T00:00:00Z"),
      new Decision.DecideInput(200, List.of(), List.of("spicy"), "", 2),
      List.of(),
      null
    );

    when(decisionHistoryService.list(any(String.class), any(DecisionHistoryService.DecisionHistoryQuery.class)))
        .thenReturn(Mono.just(new DecisionHistoryService.DecisionPage(List.of(dec1, dec2), "nextCursorHere")));

    webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("anurag")))
      .get()
      .uri("/api/decisions")
      .exchange()
      .expectStatus().isOk()
      .expectHeader().valueEquals("X-Next-Cursor", "nextCursorHere")
      .expectBody()
      .jsonPath("$[0].id").isEqualTo("dec1")
      .jsonPath("$[1].id").isEqualTo("dec2");
  }

    @Test
    void getById_rejectsWrongUser() {
    Decision decision = new Decision(
      "dec1",
      "someone-else",
      Instant.parse("2026-01-01T00:00:00Z"),
      new Decision.DecideInput(250, List.of("comfort"), List.of(), "biryani", 3),
      List.of(),
      null
    );

    when(decisionRepository.findById("dec1")).thenReturn(Mono.just(decision));

    webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("anurag")))
      .get()
      .uri("/api/decisions/dec1")
      .exchange()
      .expectStatus().isForbidden();
    }

  @Test
  void feedback_rejectsWrongUser() {
    Decision decision = new Decision(
        "dec1",
        "someone-else",
        Instant.parse("2026-01-01T00:00:00Z"),
        new Decision.DecideInput(250, List.of("comfort"), List.of(), "biryani", 3),
        List.of(),
        null
    );

    when(decisionRepository.findById("dec1")).thenReturn(Mono.just(decision));

    webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("anurag")))
      .post()
        .uri("/api/decisions/dec1/feedback")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"status\":\"REJECT\",\"reasonCode\":\"TOO_PRICEY\"}")
        .exchange()
        .expectStatus().isForbidden();
  }

  @Test
  void feedback_savesOnDecision() {
    Decision decision = new Decision(
        "dec1",
        "anurag",
        Instant.parse("2026-01-01T00:00:00Z"),
        new Decision.DecideInput(250, List.of("comfort"), List.of(), "biryani", 3),
        List.of(),
        null
    );

    when(decisionRepository.findById("dec1")).thenReturn(Mono.just(decision));

    when(decisionRepository.save(any(Decision.class)))
        .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    when(userPreferenceRepository.findById("anurag")).thenReturn(Mono.empty());
    when(userPreferenceRepository.save(any(UserPreference.class)))
      .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    when(decisionEventRepository.save(any(DecisionEvent.class)))
      .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("anurag")))
      .post()
        .uri("/api/decisions/dec1/feedback")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"status\":\"ACCEPT\",\"comment\":\"Looks good\"}")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.id").isEqualTo("dec1")
        .jsonPath("$.feedback.status").isEqualTo("ACCEPT")
        .jsonPath("$.feedback.comment").isEqualTo("Looks good")
        .jsonPath("$.feedback.createdAt").exists();
  }

  @Test
  void events_requiresAction() {
    Decision decision = new Decision(
        "dec1",
        "anurag",
        Instant.parse("2026-01-01T00:00:00Z"),
        new Decision.DecideInput(250, List.of("comfort"), List.of(), "biryani", 3),
        List.of(),
        null
    );

    when(decisionRepository.findById("dec1")).thenReturn(Mono.just(decision));

    webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("anurag")))
      .post()
        .uri("/api/decisions/dec1/events")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  void events_clickPlatform_requiresPlatform() {
    Decision decision = new Decision(
        "dec1",
        "anurag",
        Instant.parse("2026-01-01T00:00:00Z"),
        new Decision.DecideInput(250, List.of("comfort"), List.of(), "biryani", 3),
        List.of(),
        null
    );

    when(decisionRepository.findById("dec1")).thenReturn(Mono.just(decision));

    webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("anurag")))
      .post()
        .uri("/api/decisions/dec1/events")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"action\":\"CLICK_PLATFORM\"}")
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  void events_rejectsWrongUser() {
    Decision decision = new Decision(
        "dec1",
        "someone-else",
        Instant.parse("2026-01-01T00:00:00Z"),
        new Decision.DecideInput(250, List.of("comfort"), List.of(), "biryani", 3),
        List.of(),
        null
    );

    when(decisionRepository.findById("dec1")).thenReturn(Mono.just(decision));

    webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("anurag")))
      .post()
        .uri("/api/decisions/dec1/events")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"action\":\"CLICK_PLATFORM\",\"platform\":\"SWIGGY\"}")
        .exchange()
        .expectStatus().isForbidden();
  }

  @Test
  void events_savesEvent() {
    Decision decision = new Decision(
        "dec1",
        "anurag",
        Instant.parse("2026-01-01T00:00:00Z"),
        new Decision.DecideInput(250, List.of("comfort"), List.of(), "biryani", 3),
        List.of(),
        null
    );

    when(decisionRepository.findById("dec1")).thenReturn(Mono.just(decision));

    when(decisionEventRepository.save(any(DecisionEvent.class)))
      .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("anurag")))
      .post()
        .uri("/api/decisions/dec1/events")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"action\":\"CLICK_PLATFORM\",\"platform\":\"SWIGGY\",\"context\":{\"device\":\"mobile\"}}")
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.decisionId").isEqualTo("dec1")
        .jsonPath("$.userId").isEqualTo("anurag")
        .jsonPath("$.action").isEqualTo("CLICK_PLATFORM")
        .jsonPath("$.platform").isEqualTo("SWIGGY")
        .jsonPath("$.createdAt").exists();
  }
}
