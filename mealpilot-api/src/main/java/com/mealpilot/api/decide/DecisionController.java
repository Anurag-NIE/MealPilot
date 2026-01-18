package com.mealpilot.api.decide;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/decisions")
@Tag(name = "Decisions", description = "Decision history and feedback")
public class DecisionController {

  private final DecisionRepository decisionRepository;
  private final UserPreferenceRepository userPreferenceRepository;
  private final DecisionHistoryService decisionHistoryService;
  private final DecisionEventRepository decisionEventRepository;

  public DecisionController(
      DecisionRepository decisionRepository,
      UserPreferenceRepository userPreferenceRepository,
      DecisionHistoryService decisionHistoryService,
      DecisionEventRepository decisionEventRepository
  ) {
    this.decisionRepository = decisionRepository;
    this.userPreferenceRepository = userPreferenceRepository;
    this.decisionHistoryService = decisionHistoryService;
    this.decisionEventRepository = decisionEventRepository;
  }

  @GetMapping
  public Mono<ResponseEntity<List<Decision>>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "limit", required = false) Integer limit,
      @RequestParam(name = "cursor", required = false) String cursor,
      @RequestParam(name = "from", required = false) String from,
      @RequestParam(name = "to", required = false) String to,
      @RequestParam(name = "hasFeedback", required = false) Boolean hasFeedback,
      @RequestParam(name = "feedbackStatus", required = false) Decision.FeedbackStatus feedbackStatus,
      @RequestParam(name = "reasonCode", required = false) String reasonCode
  ) {
    int safeLimit = clamp(limit, 1, 200, 50);

    Instant fromInstant = parseInstantOrNull(from, "from");
    Instant toInstant = parseInstantOrNull(to, "to");
    if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be <= to"));
    }

    DecisionHistoryService.DecisionHistoryQuery query = new DecisionHistoryService.DecisionHistoryQuery(
        safeLimit,
        cursor,
        fromInstant,
        toInstant,
        hasFeedback,
        feedbackStatus,
        reasonCode
    );

    return decisionHistoryService.list(jwt.getSubject(), query)
        .map(page -> {
          ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
          if (page.nextCursor() != null && !page.nextCursor().isBlank()) {
            builder.header("X-Next-Cursor", page.nextCursor());
          }
          return builder.body(page.items());
        });
  }

  @GetMapping("/{id}")
    @Operation(summary = "Get decision detail", description = "Fetch a single persisted decision, including candidates, feedback, and reproducibility meta.")
    @ApiResponses({
      @ApiResponse(
        responseCode = "200",
        description = "Decision detail",
        content = @Content(
          mediaType = "application/json",
          schema = @Schema(implementation = Decision.class),
          examples = @ExampleObject(
            name = "decision_detail",
            value = "{\n" +
              "  \"id\": \"678b2e7e2ef2f44a3c9d8a1b\",\n" +
              "  \"userId\": \"user_123\",\n" +
              "  \"createdAt\": \"2026-01-17T12:00:00Z\",\n" +
              "  \"input\": {\"budget\": 250, \"mustHaveTags\": [\"spicy\"], \"avoidTags\": [], \"query\": \"\", \"limit\": 3},\n" +
              "  \"candidates\": [\n" +
              "    {\n" +
              "      \"item\": {\"id\": \"it1\", \"name\": \"Chicken Biryani\", \"restaurantName\": \"Spice Hub\", \"tags\": [\"spicy\",\"rice\"], \"priceEstimate\": 350},\n" +
              "      \"score\": 4.25,\n" +
              "      \"confidence\": 0.74,\n" +
              "      \"why\": [\"Matches your usual preferences\"],\n" +
              "      \"breakdown\": {\"base\": 1.0, \"budgetFit\": 0.6, \"mustTagMatch\": 1.5, \"avoidTagPenalty\": 0.0, \"queryMatch\": 0.0, \"restaurantAffinity\": 0.4, \"tagAffinity\": 0.5, \"priceSensitivity\": -0.1, \"total\": 4.25}\n" +
              "    }\n" +
              "  ],\n" +
              "  \"feedback\": {\"status\": \"REJECT\", \"reasonCode\": \"TOO_PRICEY\", \"reason\": {\"category\": \"PRICE\", \"code\": \"TOO_PRICEY\", \"tags\": []}, \"comment\": \"Too expensive today\", \"rating\": 2, \"createdAt\": \"2026-01-17T12:05:00Z\"},\n" +
              "  \"meta\": {\"schemaVersion\": 2, \"algorithm\": \"heuristic-score\", \"algorithmVersion\": \"1\", \"inputHash\": \"...\", \"itemsHash\": \"...\", \"preferenceHash\": \"...\"}\n" +
              "}"
          )
        )
      )
    })
  public Mono<Decision> getById(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String id
  ) {
    return decisionRepository.findById(id)
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "decision not found")))
        .flatMap(existing -> {
          if (!existing.userId().equals(jwt.getSubject())) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "not your decision"));
          }
          return Mono.just(existing);
        });
  }

  public record FeedbackRequest(
      @NotNull(message = "status is required")
      @Schema(description = "User feedback outcome", example = "REJECT")
      Decision.FeedbackStatus status,

      @Size(max = 64, message = "reasonCode must be <= 64 characters")
      @Schema(description = "Stable short reason code for analytics/filtering (backward compatible)", example = "TOO_PRICEY")
      String reasonCode,

      @Schema(description = "High-level taxonomy category (optional). If omitted, category may be inferred from reasonCode.", example = "PRICE")
      Decision.FeedbackReasonCategory category,

      @Schema(description = "Optional tags associated with the feedback (e.g., \"spicy\", \"veg\")")
      List<@Size(max = 32, message = "tag must be <= 32 characters") String> tags,

      @Min(value = 1, message = "rating must be >= 1")
      @Max(value = 5, message = "rating must be <= 5")
      @Schema(description = "Optional 1-5 rating", example = "2")
      Integer rating,

      @Size(max = 500, message = "comment must be <= 500 characters")
      @Schema(description = "Optional free-form comment", example = "Too expensive for today")
      String comment
  ) {}

  @PostMapping("/{id}/feedback")
    @Operation(
      summary = "Add feedback to a decision",
      description = "Stores structured feedback (status, reason taxonomy, optional rating/comment) on a past decision and updates learned preferences."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
      required = true,
      description = "Feedback payload (supports taxonomy + optional rating)",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = FeedbackRequest.class),
        examples = {
          @ExampleObject(
            name = "reject_with_taxonomy",
            value = "{\n" +
              "  \"status\": \"REJECT\",\n" +
              "  \"reasonCode\": \"TOO_PRICEY\",\n" +
              "  \"category\": \"PRICE\",\n" +
              "  \"tags\": [\"budget\"],\n" +
              "  \"rating\": 2,\n" +
              "  \"comment\": \"Too expensive for today\"\n" +
              "}"
          ),
          @ExampleObject(
            name = "skip",
            value = "{\n" +
              "  \"status\": \"SKIP\"\n" +
              "}"
          )
        }
      )
    )
  public Mono<Decision> addFeedback(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String id,
      @Valid @RequestBody FeedbackRequest body
  ) {
    return decisionRepository.findById(id)
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "decision not found")))
        .flatMap(existing -> {
          if (!existing.userId().equals(jwt.getSubject())) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "not your decision"));
          }

          Decision.FeedbackReasonCategory inferredCategory = body.category();
          if (inferredCategory == null && body.reasonCode() != null && !body.reasonCode().isBlank()) {
            inferredCategory = inferCategory(body.reasonCode());
          }

          Decision.FeedbackReason reason = null;
          if (inferredCategory != null || (body.reasonCode() != null && !body.reasonCode().isBlank())) {
            reason = new Decision.FeedbackReason(
                inferredCategory,
                body.reasonCode(),
                body.tags() == null ? List.of() : body.tags()
            );
          }

          Decision.Feedback feedback = new Decision.Feedback(
              body.status(),
              body.reasonCode(),
              reason,
              body.comment(),
              body.rating(),
              Instant.now()
          );

          Decision updated = new Decision(
              existing.id(),
              existing.userId(),
              existing.createdAt(),
              existing.input(),
              existing.candidates(),
              feedback,
              existing.meta()
          );

            return decisionRepository.save(updated)
              .flatMap(saved -> userPreferenceRepository.findById(saved.userId())
                .defaultIfEmpty(UserPreference.empty(saved.userId()))
                .map(pref -> pref.applyDecisionFeedback(saved, feedback))
                .flatMap(userPreferenceRepository::save)
                .thenReturn(saved)
              )
              .flatMap(saved -> {
                // Feedback is a first-class intent signal; record it as an immutable event as well.
                DecisionEvent event = new DecisionEvent(
                    null,
                    saved.id(),
                    saved.userId(),
                    actionFromFeedbackStatus(body.status()),
                    null,
                    null,
                    Instant.now()
                );

                return decisionEventRepository.save(event).thenReturn(saved);
              });
        });
  }

  public record CreateEventRequest(
      @NotNull(message = "action is required")
      @Schema(description = "Intent action", example = "CLICK_PLATFORM")
      DecisionEvent.Action action,

      @Schema(description = "Platform (required when action=CLICK_PLATFORM)", example = "SWIGGY")
      DecisionEvent.Platform platform,

      @Schema(description = "Optional context")
      DecisionEvent.Context context
  ) {}

  @PostMapping("/{id}/events")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Add an intent event to a decision",
      description = "Stores an immutable DecisionEvent (e.g., deep-link clicks via CLICK_PLATFORM) associated with a past decision."
  )
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      required = true,
      description = "Decision event payload",
      content = @Content(
          mediaType = "application/json",
          schema = @Schema(implementation = CreateEventRequest.class),
          examples = {
            @ExampleObject(
                name = "click_platform",
                value = "{\n" +
                    "  \"action\": \"CLICK_PLATFORM\",\n" +
                    "  \"platform\": \"SWIGGY\",\n" +
                    "  \"context\": {\"timeOfDay\": \"night\", \"device\": \"mobile\"}\n" +
                    "}"
            )
          }
      )
  )
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Created event",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = DecisionEvent.class))
    )
  })
  public Mono<DecisionEvent> addEvent(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String id,
      @Valid @RequestBody CreateEventRequest body
  ) {
    Objects.requireNonNull(jwt, "jwt");

    if (body.action() == DecisionEvent.Action.CLICK_PLATFORM && body.platform() == null) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "platform is required when action=CLICK_PLATFORM"));
    }

    return decisionRepository.findById(id)
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "decision not found")))
        .flatMap(existing -> {
          if (!existing.userId().equals(jwt.getSubject())) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "not your decision"));
          }

          DecisionEvent event = new DecisionEvent(
              null,
              existing.id(),
              existing.userId(),
              body.action(),
              body.platform(),
              body.context(),
              Instant.now()
          );

          return decisionEventRepository.save(event);
        });
  }

  private static DecisionEvent.Action actionFromFeedbackStatus(Decision.FeedbackStatus status) {
    if (status == null) {
      return null;
    }
    // Keep enums aligned by name; feedback only supports ACCEPT/REJECT/SKIP.
    return DecisionEvent.Action.valueOf(status.name());
  }

  private static Decision.FeedbackReasonCategory inferCategory(String reasonCode) {
    if (reasonCode == null) {
      return null;
    }

    String normalized = reasonCode.trim().toUpperCase();
    if (normalized.contains("PRICE") || normalized.contains("BUDGET") || normalized.equals("TOO_PRICEY")) {
      return Decision.FeedbackReasonCategory.PRICE;
    }
    if (normalized.contains("DIET") || normalized.contains("ALLERG") || normalized.contains("VEG")) {
      return Decision.FeedbackReasonCategory.DIET;
    }
    if (normalized.contains("SOLD_OUT") || normalized.contains("CLOSED") || normalized.contains("UNAVAILABLE")) {
      return Decision.FeedbackReasonCategory.AVAILABILITY;
    }
    if (normalized.contains("SAME") || normalized.contains("BORING") || normalized.contains("VARIETY")) {
      return Decision.FeedbackReasonCategory.VARIETY;
    }
    if (normalized.contains("TASTE") || normalized.contains("SPICY") || normalized.contains("SWEET")) {
      return Decision.FeedbackReasonCategory.TASTE;
    }
    return Decision.FeedbackReasonCategory.OTHER;
  }

  private static int clamp(Integer value, int min, int max, int defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    return Math.max(min, Math.min(max, value));
  }

  private static Instant parseInstantOrNull(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be an ISO-8601 instant");
    }
  }
}
