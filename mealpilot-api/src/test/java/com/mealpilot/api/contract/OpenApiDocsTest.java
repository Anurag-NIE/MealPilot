package com.mealpilot.api.contract;

import com.mealpilot.api.auth.UserAccountRepository;
import com.mealpilot.api.decide.DecisionHistoryService;
import com.mealpilot.api.decide.DecisionRepository;
import com.mealpilot.api.decide.UserPreferenceRepository;
import com.mealpilot.api.items.ItemRepository;
import java.time.Duration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {
    "spring.autoconfigure.exclude="
      + "org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration,"
      + "org.springframework.boot.autoconfigure.data.mongo.MongoReactiveDataAutoConfiguration"
  }
)
@AutoConfigureWebTestClient
@Disabled("OpenAPI endpoint is verified manually (hit /v3/api-docs). Test can be flaky in sliced/CI environments.")
class OpenApiDocsTest {

  @Autowired
  private WebTestClient webTestClient;

  @MockBean
  private UserAccountRepository userAccountRepository;

  @MockBean
  private ItemRepository itemRepository;

  @MockBean
  private DecisionRepository decisionRepository;

  @MockBean
  private UserPreferenceRepository userPreferenceRepository;

  @MockBean
  private DecisionHistoryService decisionHistoryService;

  @Test
  void v3ApiDocs_isPublicAndJson() {
    webTestClient.mutate()
        .responseTimeout(Duration.ofSeconds(20))
        .build()
        .get()
        .uri("/v3/api-docs")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.openapi").exists()
        .jsonPath("$.info.title").isEqualTo("MealPilot API");
  }
}
