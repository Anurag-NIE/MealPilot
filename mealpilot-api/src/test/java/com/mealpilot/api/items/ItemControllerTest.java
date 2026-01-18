package com.mealpilot.api.items;

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

@WebFluxTest(controllers = ItemController.class)
@Import(SecurityConfig.class)
class ItemControllerTest {

  @Autowired
  private WebTestClient webTestClient;

  @MockBean
  private ItemRepository repo;

  @MockBean
  private ItemHistoryService itemHistoryService;

  @Test
  void list_returnsUserItems() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    Item item = new Item(
        "id1",
        "anurag",
        "Chicken Biryani",
        "Hyderabadi Biryani House",
        List.of("comfort"),
      List.of("swiggy"),
        199,
        true,
        now,
        now
    );

    when(itemHistoryService.list(org.mockito.ArgumentMatchers.eq("anurag"), org.mockito.ArgumentMatchers.any(ItemHistoryService.ItemHistoryQuery.class)))
      .thenReturn(Mono.just(new ItemHistoryService.ItemPage(List.of(item), null)));

    webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("anurag")))
        .get()
        .uri("/api/items")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$[0].id").isEqualTo("id1")
        .jsonPath("$[0].userId").isEqualTo("anurag")
        .jsonPath("$[0].name").isEqualTo("Chicken Biryani");
  }

  @Test
  void list_requiresAuth() {
    webTestClient.get()
        .uri("/api/items")
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  void create_requiresName() {
    webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("anurag")))
      .post()
        .uri("/api/items")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  void create_persistsItem() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    Item saved = new Item(
        "id1",
        "anurag",
        "Chicken Biryani",
        "Hyderabadi Biryani House",
        List.of("comfort"),
      List.of("swiggy"),
        199,
        true,
        now,
        now
    );

    when(repo.save(any(Item.class))).thenReturn(Mono.just(saved));

    webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("anurag")))
      .post()
        .uri("/api/items")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"name\":\"Chicken Biryani\",\"restaurantName\":\"Hyderabadi Biryani House\",\"tags\":[\"comfort\"],\"platformHints\":[\"SWIGGY\"],\"priceEstimate\":199}")
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").isEqualTo("id1")
        .jsonPath("$.name").isEqualTo("Chicken Biryani")
        .jsonPath("$.restaurantName").isEqualTo("Hyderabadi Biryani House")
        .jsonPath("$.platformHints[0]").isEqualTo("swiggy");
  }
}
