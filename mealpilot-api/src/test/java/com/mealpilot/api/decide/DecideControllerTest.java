package com.mealpilot.api.decide;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import com.mealpilot.api.config.SecurityConfig;
import com.mealpilot.api.decide.Decision.Feedback;
import com.mealpilot.api.items.Item;
import com.mealpilot.api.items.ItemRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = DecideController.class)
@Import(SecurityConfig.class)
class DecideControllerTest {

  @Autowired
  private WebTestClient webTestClient;

  @MockBean
  private ItemRepository itemRepository;

    @MockBean
    private DecisionRepository decisionRepository;

    @MockBean
    private UserPreferenceRepository userPreferenceRepository;

  @Test
  void decide_returnsTopCandidates_withConfidenceAndWhy() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");

    Item cheapComfort = new Item(
        "id1",
        "anurag",
        "Chicken Biryani",
        "Hyderabadi Biryani House",
        List.of("comfort"),
        List.of(),
        199,
        true,
        now,
        now
    );

    Item expensive = new Item(
        "id2",
        "anurag",
        "Mutton Biryani",
        "Fancy Place",
        List.of("comfort"),
        List.of(),
        499,
        true,
        now,
        now
    );

    Item tagMatch = new Item(
        "id3",
        "anurag",
        "Paneer Tikka",
        "Punjabi Dhaba",
        List.of("veg"),
        List.of(),
        220,
        true,
        now,
        now
    );

    when(itemRepository.findAllByUserIdAndActiveIsTrue("anurag"))
        .thenReturn(Flux.just(expensive, tagMatch, cheapComfort));

    when(userPreferenceRepository.findById("anurag")).thenReturn(Mono.empty());

    when(decisionRepository.save(org.mockito.ArgumentMatchers.any(Decision.class)))
        .thenAnswer(inv -> {
          Decision d = inv.getArgument(0);
          return Mono.just(new Decision(
              "dec1",
              d.userId(),
              d.createdAt(),
              d.input(),
              d.candidates(),
              (Feedback) null
          ));
        });

    webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("anurag")))
        .post()
        .uri("/api/decide")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{" +
            "\"budget\":250," +
            "\"mustHaveTags\":[\"comfort\"]," +
            "\"query\":\"biryani\"," +
            "\"limit\":3" +
            "}")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.decisionId").isEqualTo("dec1")
        .jsonPath("$.userId").isEqualTo("anurag")
        .jsonPath("$.limit").isEqualTo(3)
        .jsonPath("$.candidates.length()").isEqualTo(3)
        // top result should be cheap comfort biryani (budget fit + comfort tag + query match)
        .jsonPath("$.candidates[0].item.id").isEqualTo("id1")
        .jsonPath("$.candidates[0].confidence").isNumber()
        .jsonPath("$.candidates[0].why").isArray()
        .jsonPath("$.candidates[0].deepLinks").isArray()
        .jsonPath("$.candidates[0].deepLinks[0].platform").isEqualTo("SWIGGY");
  }

  @Test
  void decide_clampsLimitToMax3() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");

    when(itemRepository.findAllByUserIdAndActiveIsTrue("anurag"))
        .thenReturn(Flux.just(
            new Item("id1", "anurag", "A", null, List.of(), List.of(), 100, true, now, now),
            new Item("id2", "anurag", "B", null, List.of(), List.of(), 100, true, now, now),
            new Item("id3", "anurag", "C", null, List.of(), List.of(), 100, true, now, now),
            new Item("id4", "anurag", "D", null, List.of(), List.of(), 100, true, now, now)
        ));

    when(userPreferenceRepository.findById("anurag")).thenReturn(Mono.empty());

    when(decisionRepository.save(org.mockito.ArgumentMatchers.any(Decision.class)))
        .thenAnswer(inv -> {
          Decision d = inv.getArgument(0);
          return Mono.just(new Decision(
              "dec1",
              d.userId(),
              d.createdAt(),
              d.input(),
              d.candidates(),
              (Feedback) null
          ));
        });

    webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("anurag")))
        .post()
        .uri("/api/decide")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"limit\":10}")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.limit").isEqualTo(3)
        .jsonPath("$.candidates.length()").isEqualTo(3);
  }

  @Test
  void decide_appliesLearnedPreferencesToRanking() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");

    Item comfort = new Item(
        "id1",
        "anurag",
        "Chicken Biryani",
        "Place A",
        List.of("comfort"),
        List.of(),
        200,
        true,
        now,
        now
    );

    Item veg = new Item(
        "id2",
        "anurag",
        "Paneer Tikka",
        "Place B",
        List.of("veg"),
        List.of(),
        200,
        true,
        now,
        now
    );

    when(itemRepository.findAllByUserIdAndActiveIsTrue("anurag"))
        .thenReturn(Flux.just(comfort, veg));

    when(userPreferenceRepository.findById("anurag")).thenReturn(Mono.just(
        new UserPreference(
            "anurag",
            Map.of("veg", 5),
            Map.of(),
            0,
            now
        )
    ));

    when(decisionRepository.save(org.mockito.ArgumentMatchers.any(Decision.class)))
        .thenAnswer(inv -> {
          Decision d = inv.getArgument(0);
          return Mono.just(new Decision(
              "dec1",
              d.userId(),
              d.createdAt(),
              d.input(),
              d.candidates(),
              null
          ));
        });

    webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("anurag")))
        .post()
        .uri("/api/decide")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"limit\":2}")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.candidates.length()").isEqualTo(2)
        // without constraints, base score ties; learned veg preference should win
        .jsonPath("$.candidates[0].item.id").isEqualTo("id2")
        .jsonPath("$.candidates[0].why").isArray();
  }
}
