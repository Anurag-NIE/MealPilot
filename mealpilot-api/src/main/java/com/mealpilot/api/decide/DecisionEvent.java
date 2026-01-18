package com.mealpilot.api.decide;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("decision_events")
@CompoundIndex(
    name = "decision_events_user_createdAt_id_desc",
    def = "{ 'userId': 1, 'createdAt': -1, '_id': -1 }"
)
@CompoundIndex(
    name = "decision_events_decision_createdAt_id_desc",
    def = "{ 'decisionId': 1, 'createdAt': -1, '_id': -1 }"
)
@Schema(
    name = "DecisionEvent",
    description = "Immutable intent event associated with a decision (feedback actions, deep-link clicks, etc.)"
)
public record DecisionEvent(
    @Schema(description = "Event id", example = "678b2e7e2ef2f44a3c9d8a1c")
    @Id String id,

    @Schema(description = "Decision id", example = "678b2e7e2ef2f44a3c9d8a1b")
    @Indexed String decisionId,

    @Schema(description = "Owner user id", example = "user_123")
    @Indexed String userId,

    @Schema(description = "Intent action", example = "CLICK_PLATFORM")
    Action action,

    @Schema(description = "Platform (required for CLICK_PLATFORM)", example = "SWIGGY")
    Platform platform,

    @Schema(description = "Optional context", implementation = Context.class)
    Context context,

    @Schema(description = "Creation timestamp", example = "2026-01-17T12:05:00Z")
    @Indexed(direction = IndexDirection.DESCENDING) Instant createdAt
) {

  @Schema(name = "DecisionEventAction", description = "Decision event action")
  public enum Action {
    ACCEPT,
    REJECT,
    SKIP,
    CLICK_PLATFORM
  }

  @Schema(name = "DecisionEventPlatform", description = "Delivery platform")
  public enum Platform {
    SWIGGY,
    ZOMATO,
    EATSURE
  }

  @Schema(name = "DecisionEventContext", description = "Optional context captured with an intent event")
  public record Context(
      @Schema(description = "Time of day hint", example = "night")
      String timeOfDay,
      @Schema(description = "Device hint", example = "mobile")
      String device,
      @Schema(description = "Location hint", example = "home")
      String locationHint
  ) {}
}
