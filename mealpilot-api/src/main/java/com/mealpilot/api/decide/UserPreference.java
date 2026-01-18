package com.mealpilot.api.decide;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("user_preferences")
public record UserPreference(
    @Id String userId,
    Map<String, Integer> tagWeights,
    Map<String, Integer> restaurantWeights,
    int pricePenalty,
    Instant updatedAt,
    Integer schemaVersion,
    PreferenceProfile profile
) {

  public UserPreference(
      String userId,
      Map<String, Integer> tagWeights,
      Map<String, Integer> restaurantWeights,
      int pricePenalty,
      Instant updatedAt
  ) {
    this(userId, tagWeights, restaurantWeights, pricePenalty, updatedAt, null, null);
  }

  public static UserPreference empty(String userId) {
    return new UserPreference(userId, Map.of(), Map.of(), 0, Instant.now(), 2, PreferenceProfile.empty());
  }

  public PreferenceProfile effectiveProfile() {
    return profile == null ? PreferenceProfile.empty() : profile;
  }

  public UserPreference withProfile(PreferenceProfile profile) {
    return new UserPreference(
        userId,
        tagWeights == null ? Map.of() : tagWeights,
        restaurantWeights == null ? Map.of() : restaurantWeights,
        pricePenalty,
        Instant.now(),
        2,
        profile == null ? PreferenceProfile.empty() : profile
    );
  }

  public int tagWeightFor(String tag) {
    if (tag == null || tag.isBlank() || tagWeights == null || tagWeights.isEmpty()) {
      return 0;
    }
    return tagWeights.getOrDefault(normalize(tag), 0);
  }

  public int restaurantWeightFor(String restaurantName) {
    if (restaurantName == null || restaurantName.isBlank() || restaurantWeights == null || restaurantWeights.isEmpty()) {
      return 0;
    }
    return restaurantWeights.getOrDefault(normalize(restaurantName), 0);
  }

  public UserPreference applyDecisionFeedback(Decision decision, Decision.Feedback feedback) {
    if (decision == null || feedback == null || decision.candidates() == null || decision.candidates().isEmpty()) {
      return this;
    }

      if (feedback.status() == Decision.FeedbackStatus.SKIP) {
        return this;
      }
    Decision.ItemSnapshot topItem = decision.candidates().get(0).item();
    if (topItem == null) {
      return this;
    }

    int delta = feedback.status() == Decision.FeedbackStatus.ACCEPT ? 1 : -1;

    Map<String, Integer> newTagWeights = new HashMap<>(tagWeights == null ? Map.of() : tagWeights);
    if (topItem.tags() != null) {
      for (String tag : topItem.tags()) {
        if (tag == null || tag.isBlank()) {
          continue;
        }
        bump(newTagWeights, normalize(tag), delta);
      }
    }

    Map<String, Integer> newRestaurantWeights = new HashMap<>(restaurantWeights == null ? Map.of() : restaurantWeights);
    if (topItem.restaurantName() != null && !topItem.restaurantName().isBlank()) {
      bump(newRestaurantWeights, normalize(topItem.restaurantName()), delta);
    }

    int newPricePenalty = pricePenalty;
    if (feedback.status() == Decision.FeedbackStatus.REJECT
        && feedback.reasonCode() != null
        && feedback.reasonCode().equalsIgnoreCase("TOO_PRICEY")) {
      newPricePenalty = clamp(pricePenalty + 1, 0, 5);
    }

    if (feedback.status() == Decision.FeedbackStatus.ACCEPT) {
      newPricePenalty = clamp(pricePenalty - 1, 0, 5);
    }

    return new UserPreference(
        userId,
        Map.copyOf(newTagWeights),
        Map.copyOf(newRestaurantWeights),
        newPricePenalty,
        Instant.now(),
        schemaVersion == null ? 2 : schemaVersion,
        profile
    );
  }

  public record PreferenceProfile(
      Integer budgetMin,
      Integer budgetMax,
      java.util.Set<String> preferTags,
      java.util.Set<String> avoidTags,
      java.util.Set<String> preferRestaurants,
      java.util.Set<String> avoidRestaurants,
      java.util.Set<String> dietaryRestrictions,
      java.util.Set<String> allergens,
      String notes
  ) {
    public static PreferenceProfile empty() {
      return new PreferenceProfile(
          null,
          null,
          java.util.Set.of(),
          java.util.Set.of(),
          java.util.Set.of(),
          java.util.Set.of(),
          java.util.Set.of(),
          java.util.Set.of(),
          null
      );
    }
  }

  private static void bump(Map<String, Integer> map, String key, int delta) {
    int next = clamp(map.getOrDefault(key, 0) + delta, -5, 5);
    if (next == 0) {
      map.remove(key);
    } else {
      map.put(key, next);
    }
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static String normalize(String s) {
    return s.trim().toLowerCase(Locale.ROOT);
  }
}
