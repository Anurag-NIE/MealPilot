package com.mealpilot.api.decide;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("decisions")
@CompoundIndex(
    name = "decisions_user_createdAt_id_desc",
    def = "{ 'userId': 1, 'createdAt': -1, '_id': -1 }"
)
@Schema(name = "Decision", description = "A persisted decision result (input, ranked candidates, optional feedback, and reproducibility metadata)")
public record Decision(
    @Schema(description = "Decision id", example = "678b2e7e2ef2f44a3c9d8a1b")
    @Id String id,

    @Schema(description = "Owner user id", example = "user_123")
    String userId,

    @Schema(description = "Creation timestamp", example = "2026-01-17T12:00:00Z")
    @Indexed(direction = IndexDirection.DESCENDING) Instant createdAt,

    @Schema(description = "Input used for the decision")
    DecideInput input,

    @Schema(description = "Ranked candidate snapshots")
    List<CandidateSnapshot> candidates,

    @Schema(description = "Optional feedback attached to this decision")
    Feedback feedback,

    @Schema(description = "Optional reproducibility metadata")
    DecisionMeta meta
) {

    public Decision(
            String id,
            String userId,
            Instant createdAt,
            DecideInput input,
            List<CandidateSnapshot> candidates,
            Feedback feedback
    ) {
        this(id, userId, createdAt, input, candidates, feedback, null);
    }

  @Schema(name = "DecideInput", description = "Input payload captured at decision time")
  public record DecideInput(
      @Schema(description = "Budget", example = "250")
      Integer budget,

      @Schema(description = "Must-have tags", example = "[\"spicy\"]")
      List<String> mustHaveTags,

      @Schema(description = "Avoid tags", example = "[\"peanut\"]")
      List<String> avoidTags,

      @Schema(description = "Query", example = "biryani")
      String query,

      @Schema(description = "Limit", example = "3")
      Integer limit
  ) {}

    @Schema(name = "CandidateSnapshot", description = "Snapshot of a single ranked candidate")
    public record CandidateSnapshot(
            ItemSnapshot item,
            @Schema(example = "4.25") double score,
            @Schema(example = "0.74") double confidence,
            @Schema(description = "Short explanation bullets", example = "[\"Matches your usual preferences\"]")
            List<String> why,
            List<DeepLink> deepLinks,
            ScoreBreakdown breakdown
        ) {
        public CandidateSnapshot(
                ItemSnapshot item,
                double score,
                double confidence,
                List<String> why
        ) {
            this(item, score, confidence, why, List.of(), null);
        }

        public CandidateSnapshot(
                ItemSnapshot item,
                double score,
                double confidence,
                List<String> why,
                List<DeepLink> deepLinks
        ) {
            this(item, score, confidence, why, deepLinks, null);
        }
    }

    @Schema(name = "DeepLink", description = "Deep link for a candidate on a delivery platform")
    public record DeepLink(
        @Schema(example = "SWIGGY") String platform,
        @Schema(example = "https://www.swiggy.com/search?query=chicken%20biryani") String url
    ) {}

    @Schema(name = "ScoreBreakdown", description = "Explainability breakdown of the scoring heuristic")
    public record ScoreBreakdown(
      double base,
      double budgetFit,
      double mustTagMatch,
      double avoidTagPenalty,
      double queryMatch,
      double restaurantAffinity,
      double tagAffinity,
      double priceSensitivity,
      double total
  ) {}

    @Schema(name = "ItemSnapshot", description = "Snapshot of an item at decision time")
    public record ItemSnapshot(
      String id,
      String name,
      String restaurantName,
      List<String> tags,
      Integer priceEstimate
  ) {}

  @Schema(name = "DecisionFeedback", description = "Structured feedback on a decision")
  public record Feedback(
      FeedbackStatus status,
      String reasonCode,
            FeedbackReason reason,
      String comment,
            Integer rating,
      Instant createdAt
    ) {
        public Feedback(
                FeedbackStatus status,
                String reasonCode,
                String comment,
                Instant createdAt
        ) {
            this(status, reasonCode, null, comment, null, createdAt);
        }
    }

        @Schema(name = "FeedbackReason", description = "Taxonomy details for feedback")
        public record FeedbackReason(
            FeedbackReasonCategory category,
            String code,
            List<String> tags
    ) {}

    @Schema(name = "FeedbackReasonCategory", description = "High-level reason category")
    public enum FeedbackReasonCategory {
        PRICE,
        TASTE,
        DIET,
        AVAILABILITY,
        VARIETY,
        OTHER
    }

    @Schema(name = "FeedbackStatus", description = "Feedback outcome")
    public enum FeedbackStatus {
    ACCEPT,
        REJECT,
        SKIP
  }

    @Schema(name = "DecisionMeta", description = "Reproducibility metadata for a decision")
    public record DecisionMeta(
      Integer schemaVersion,
      String algorithm,
      String algorithmVersion,
      String inputHash,
      String itemsHash,
      String preferenceHash,
      Long randomSeed,
      UserPreferenceSnapshot preferenceSnapshot
  ) {}

    @Schema(name = "UserPreferenceSnapshot", description = "Snapshot of preference signals at decision time")
    public record UserPreferenceSnapshot(
      Integer schemaVersion,
      java.util.Map<String, Integer> tagWeights,
      java.util.Map<String, Integer> restaurantWeights,
      Integer pricePenalty,
      java.time.Instant updatedAt
  ) {}
}
