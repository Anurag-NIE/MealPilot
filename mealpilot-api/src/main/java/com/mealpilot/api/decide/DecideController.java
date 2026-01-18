package com.mealpilot.api.decide;

import com.mealpilot.api.common.Hashing;
import com.mealpilot.api.items.Item;
import com.mealpilot.api.items.ItemRepository;
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
import jakarta.validation.constraints.Size;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/decide")
@Tag(name = "Decide", description = "Rank candidate items and persist a decision record")
public class DecideController {

  private static final int MIN_LIMIT = 1;
  private static final int MAX_LIMIT = 50;

  private static final int DECISION_SCHEMA_VERSION = 2;
  private static final String ALGORITHM = "heuristic-score";
  private static final String ALGORITHM_VERSION = "1";

  private final ItemRepository itemRepository;
  private final DecisionRepository decisionRepository;
  private final UserPreferenceRepository userPreferenceRepository;

  public DecideController(
      ItemRepository itemRepository,
      DecisionRepository decisionRepository,
      UserPreferenceRepository userPreferenceRepository
  ) {
    this.itemRepository = itemRepository;
    this.decisionRepository = decisionRepository;
    this.userPreferenceRepository = userPreferenceRepository;
  }

  public record DecideRequest(
      @Min(value = 0, message = "budget must be >= 0")
      @Max(value = 100000, message = "budget must be <= 100000")
      @Schema(description = "Optional budget bound (used along with preference profile budgetMax)", example = "250")
      Integer budget,

      @Size(max = 20, message = "mustHaveTags must have <= 20 entries")
      @Schema(description = "Tags that must be present on candidate items", example = "[\"spicy\",\"comfort\"]")
      List<@Size(max = 32, message = "tag must be <= 32 characters") String> mustHaveTags,

      @Size(max = 20, message = "avoidTags must have <= 20 entries")
      @Schema(description = "Tags to avoid (soft penalty). Explicit profile avoidTags/diet/allergen constraints apply too.", example = "[\"peanut\"]")
      List<@Size(max = 32, message = "tag must be <= 32 characters") String> avoidTags,

      @Size(max = 200, message = "query must be <= 200 characters")
        @Schema(description = "Optional free-text query to bias ranking", example = "biryani")
      String query,

        @Schema(description = "Number of candidates to return (min 1; capped server-side)", example = "8")
      Integer limit
  ) {}

  public record DecideResponse(
      @Schema(description = "Persisted decision id (null when no candidates)", example = "678b2e7e2ef2f44a3c9d8a1b")
      String decisionId,
      @Schema(description = "Authenticated user id", example = "user_123")
      String userId,
      @Schema(description = "ISO-8601 timestamp", example = "2026-01-17T12:00:00Z")
      String time,
      @Schema(description = "Candidate limit applied", example = "3")
      int limit,
      List<Candidate> candidates,
      String message
  ) {}

  public record Candidate(
      ItemSummary item,
      @Schema(description = "Overall score", example = "4.25")
      double score,
      @Schema(description = "0..1 confidence heuristic", example = "0.74")
      double confidence,
      List<String> why,
      List<Decision.DeepLink> deepLinks,
      Decision.ScoreBreakdown breakdown
  ) {}

  public record ItemSummary(
      String id,
      String name,
      String restaurantName,
      List<String> tags,
      Integer priceEstimate
  ) {}

  @PostMapping
    @Operation(
      summary = "Rank items (Decide)",
      description = "Ranks up to N items using request signals + learned weights + explicit PreferenceProfile constraints, and persists a Decision with reproducibility metadata."
    )
    @ApiResponses({
      @ApiResponse(
        responseCode = "200",
        description = "Ranked candidates (and persisted decisionId when items exist)",
        content = @Content(
          mediaType = "application/json",
          schema = @Schema(implementation = DecideResponse.class),
          examples = @ExampleObject(
            name = "decide_ok",
            value = "{\n" +
              "  \"decisionId\": \"678b2e7e2ef2f44a3c9d8a1b\",\n" +
              "  \"userId\": \"user_123\",\n" +
              "  \"time\": \"2026-01-17T12:00:00Z\",\n" +
              "  \"limit\": 3,\n" +
              "  \"candidates\": [\n" +
              "    {\n" +
              "      \"item\": {\"id\": \"it1\", \"name\": \"Chicken Biryani\", \"restaurantName\": \"Spice Hub\", \"tags\": [\"spicy\",\"rice\"], \"priceEstimate\": 350},\n" +
              "      \"score\": 4.25,\n" +
              "      \"confidence\": 0.74,\n" +
              "      \"why\": [\"Matches your usual preferences\",\"Budget fit\"],\n" +
              "      \"deepLinks\": [{\"platform\": \"SWIGGY\", \"url\": \"https://www.swiggy.com/search?query=chicken%20biryani\"}],\n" +
              "      \"breakdown\": {\"base\": 1.0, \"budgetFit\": 0.6, \"mustTagMatch\": 1.5, \"avoidTagPenalty\": 0.0, \"queryMatch\": 0.0, \"restaurantAffinity\": 0.4, \"tagAffinity\": 0.5, \"priceSensitivity\": -0.1, \"total\": 4.25}\n" +
              "    }\n" +
              "  ],\n" +
              "  \"message\": null\n" +
              "}"
          )
        )
      )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
      required = false,
      description = "Optional decide request. Empty body uses defaults.",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = DecideRequest.class),
        examples = {
          @ExampleObject(
            name = "decide_default",
            summary = "Defaults",
            value = "{}"
          ),
          @ExampleObject(
            name = "decide_with_constraints",
            summary = "With budget/tags/query",
            value = "{\n" +
              "  \"budget\": 250,\n" +
              "  \"mustHaveTags\": [\"spicy\"],\n" +
              "  \"avoidTags\": [\"peanut\"],\n" +
              "  \"query\": \"biryani\",\n" +
              "  \"limit\": 3\n" +
              "}"
          )
        }
      )
    )
  public Mono<DecideResponse> decide(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody(required = false) DecideRequest body
  ) {
    String userId = jwt.getSubject();
    DecideRequest safeBody = body == null ? new DecideRequest(null, null, null, null, null) : body;
    int limit = clampLimit(safeBody.limit());

    Mono<UserPreference> preferenceMono = userPreferenceRepository.findById(userId)
      .defaultIfEmpty(UserPreference.empty(userId));

    return preferenceMono
      .flatMap(preference -> itemRepository.findAllByUserIdAndActiveIsTrue(userId)
        .collectList()
        .flatMap(items -> buildAndPersistResponse(userId, safeBody, limit, items, preference))
      );
  }

  private Mono<DecideResponse> buildAndPersistResponse(
      String userId,
      DecideRequest request,
      int limit,
      List<Item> items,
      UserPreference preference
  ) {
    Instant now = Instant.now();

    if (items == null || items.isEmpty()) {
      return Mono.just(new DecideResponse(
        null,
          userId,
          now.toString(),
          limit,
          List.of(),
          "No saved items yet. Create a few via POST /api/items to get decisions."
      ));
    }

    UserPreference.PreferenceProfile profile = preference == null
      ? UserPreference.PreferenceProfile.empty()
      : preference.effectiveProfile();

    Set<String> mustTags = normalizeTagSet(request.mustHaveTags());
    Set<String> requestAvoidTags = normalizeTagSet(request.avoidTags());
    String query = normalizeText(request.query());

    // Apply explicit profile constraints.
    // - Profile avoid tags are treated as a stronger signal than request avoid tags.
    // - Dietary restrictions and allergens are treated as hard-avoid tags.
    Set<String> profileAvoidTags = profile.avoidTags() == null ? Set.of() : profile.avoidTags();
    Set<String> hardAvoidTags = union(
      profile.dietaryRestrictions() == null ? Set.of() : profile.dietaryRestrictions(),
      profile.allergens() == null ? Set.of() : profile.allergens()
    );

    Integer effectiveBudget = request.budget();
    if (profile.budgetMax() != null) {
      effectiveBudget = (effectiveBudget == null)
        ? profile.budgetMax()
        : Math.min(effectiveBudget, profile.budgetMax());
    }

    List<Scored> scored = new ArrayList<>(items.size());
    for (Item item : items) {
      scored.add(score(
          item,
          effectiveBudget,
          mustTags,
          requestAvoidTags,
          query,
          preference,
          profile,
          profileAvoidTags,
          hardAvoidTags
      ));
    }

    scored.sort(
      Comparator
        .comparingDouble(Scored::score).reversed()
        .thenComparing((Scored s) -> s.item().updatedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing((Scored s) -> s.item().createdAt(), Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(s -> s.item().id(), Comparator.nullsLast(Comparator.naturalOrder()))
    );

    List<Scored> top = scored.stream().limit(limit).toList();
    List<Double> confidences = softmax(top.stream().map(Scored::score).toList());

    List<Candidate> candidates = new ArrayList<>(top.size());
    for (int i = 0; i < top.size(); i++) {
      Scored s = top.get(i);
      candidates.add(new Candidate(
          toSummary(s.item()),
          s.score(),
          confidences.get(i),
          s.why(),
          deepLinksFor(s.item()),
          s.breakdown()
      ));
    }

    Decision.DecideInput input = new Decision.DecideInput(
      request.budget(),
      request.mustHaveTags(),
      request.avoidTags(),
      request.query(),
      limit
    );

    List<Decision.CandidateSnapshot> storedCandidates = candidates.stream()
      .map(c -> new Decision.CandidateSnapshot(
        new Decision.ItemSnapshot(
          c.item().id(),
          c.item().name(),
          c.item().restaurantName(),
          c.item().tags(),
          c.item().priceEstimate()
        ),
        c.score(),
        c.confidence(),
        c.why(),
        c.deepLinks(),
        c.breakdown()
      ))
      .toList();

    Decision.DecisionMeta meta = new Decision.DecisionMeta(
        DECISION_SCHEMA_VERSION,
        ALGORITHM,
        ALGORITHM_VERSION,
        hashInput(request, limit),
        hashItems(items),
        hashPreference(preference),
        null,
        snapshotPreference(preference)
    );

    Decision decision = new Decision(
      null,
      userId,
      now,
      input,
      storedCandidates,
      null,
      meta
    );

    return decisionRepository.save(decision)
      .map(saved -> new DecideResponse(saved.id(), userId, now.toString(), limit, candidates, null));
  }

  private static ItemSummary toSummary(Item item) {
    return new ItemSummary(
        item.id(),
        item.name(),
        item.restaurantName(),
        item.tags() == null ? List.of() : item.tags(),
        item.priceEstimate()
    );
  }

  private static List<Decision.DeepLink> deepLinksFor(Item item) {
    if (item == null) {
      return List.of();
    }

    String query = buildPlatformQuery(item);
    if (query.isBlank()) {
      return List.of();
    }

    String enc = URLEncoder.encode(query, StandardCharsets.UTF_8);
    List<String> hints = item.platformHints() == null ? List.of() : item.platformHints();

    // If no hints are present, return a reasonable default set.
    List<String> platforms = hints.isEmpty() ? List.of("swiggy", "zomato") : hints;

    List<Decision.DeepLink> out = new ArrayList<>();
    for (String p : platforms) {
      String platform = normalizeText(p);
      if (platform.isBlank()) {
        continue;
      }
      switch (platform) {
        case "swiggy" -> out.add(new Decision.DeepLink("SWIGGY", "https://www.swiggy.com/search?query=" + enc));
        case "zomato" -> out.add(new Decision.DeepLink("ZOMATO", "https://www.zomato.com/search?q=" + enc));
        case "eatsure" -> out.add(new Decision.DeepLink("EATSURE", "https://www.eatsure.com/search?q=" + enc));
        default -> {
          // Ignore unknown hints (forward-compatible).
        }
      }
    }

    return out;
  }

  private static String buildPlatformQuery(Item item) {
    String name = item.name() == null ? "" : item.name().trim();
    String restaurant = item.restaurantName() == null ? "" : item.restaurantName().trim();

    if (!restaurant.isBlank()) {
      return restaurant + " " + name;
    }
    return name;
  }

  private record Scored(Item item, double score, List<String> why, Decision.ScoreBreakdown breakdown) {}

  private static Scored score(
      Item item,
      Integer budget,
      Set<String> mustTags,
      Set<String> requestAvoidTags,
      String query,
      UserPreference preference,
      UserPreference.PreferenceProfile profile,
      Set<String> profileAvoidTags,
      Set<String> hardAvoidTags
  ) {
    double base = 1.0;
    double score = base;
    List<String> why = new ArrayList<>();

    double budgetFit = 0.0;
    double mustTagMatch = 0.0;
    double avoidTagPenalty = 0.0;
    double queryMatch = 0.0;
    double restaurantAffinity = 0.0;
    double tagAffinity = 0.0;
    double priceSensitivity = 0.0;

    // Budget fit
    if (budget != null && item.priceEstimate() != null) {
      if (item.priceEstimate() <= budget) {
        budgetFit = 1.2;
        score += budgetFit;
        why.add("Within budget (≤ " + budget + ")");
      } else {
        budgetFit = -0.8;
        score += budgetFit;
        why.add("Above budget (> " + budget + ")");
      }
    }

    // Tag matches
    Set<String> itemTags = normalizeTagSet(item.tags());
    if (!mustTags.isEmpty()) {
      long matched = mustTags.stream().filter(itemTags::contains).count();
      if (matched > 0) {
        mustTagMatch = matched * 0.7;
        score += mustTagMatch;
        for (String t : mustTags) {
          if (itemTags.contains(t)) {
            why.add("Matches tag: " + t);
          }
        }
      } else {
        mustTagMatch = -0.4;
        score += mustTagMatch;
      }
    }

    // Hard avoids from explicit profile (diet/allergen).
    if (hardAvoidTags != null && !hardAvoidTags.isEmpty()) {
      Set<String> matched = intersect(itemTags, hardAvoidTags);
      if (!matched.isEmpty()) {
        double penalty = -matched.size() * 5.0;
        avoidTagPenalty += penalty;
        score += penalty;
        for (String t : matched) {
          why.add("Hard avoid tag: " + t);
        }
      }
    }

    // Avoid tags from explicit profile (stronger than request avoid tags).
    if (profileAvoidTags != null && !profileAvoidTags.isEmpty()) {
      Set<String> matched = intersect(itemTags, profileAvoidTags);
      if (!matched.isEmpty()) {
        double penalty = -matched.size() * 3.0;
        avoidTagPenalty += penalty;
        score += penalty;
        for (String t : matched) {
          why.add("Avoid tag (profile): " + t);
        }
      }
    }

    // Avoid tags from the request.
    if (requestAvoidTags != null && !requestAvoidTags.isEmpty()) {
      Set<String> matched = intersect(itemTags, requestAvoidTags);
      if (!matched.isEmpty()) {
        double penalty = -matched.size() * 1.5;
        avoidTagPenalty += penalty;
        score += penalty;
        for (String t : matched) {
          why.add("Avoid tag present: " + t);
        }
      }
    }

    // Text match (query / voice transcript)
    if (query != null && !query.isBlank()) {
      String haystack = (
          normalizeText(item.name()) + " " + normalizeText(item.restaurantName()) + " " +
              String.join(" ", itemTags)
      ).trim();

      List<String> terms = List.of(query.split("\\s+"))
          .stream()
          .filter(t -> t.length() >= 3)
          .toList();

      long hits = terms.stream().filter(haystack::contains).count();
      if (hits > 0) {
        queryMatch = hits * 0.5;
        score += queryMatch;
        why.add("Matches your query");
      }
    }

    if (why.isEmpty()) {
      why.add("A safe default based on your saved items");
    }

    // Learned preferences (lightweight personalization)
    if (preference != null) {
      int restaurantWeight = preference.restaurantWeightFor(item.restaurantName());
      if (restaurantWeight != 0) {
        double delta = restaurantWeight * 0.25;
        restaurantAffinity += delta;
        score += delta;
        why.add(restaurantWeight > 0 ? "You often like this place" : "You often avoid this place");
      }

      int tagWeightSum = 0;
      for (String tag : itemTags) {
        int w = preference.tagWeightFor(tag);
        if (w != 0) {
          tagWeightSum += w;
        }
      }
      if (tagWeightSum != 0) {
        double delta = tagWeightSum * 0.15;
        tagAffinity += delta;
        score += delta;
        why.add(tagWeightSum > 0 ? "Matches your usual preferences" : "Conflicts with your usual preferences");
      }

      // Price sensitivity learning from "TOO_PRICEY" rejections.
      if (budget != null && item.priceEstimate() != null && item.priceEstimate() > budget) {
        int penalty = preference.pricePenalty();
        if (penalty > 0) {
          priceSensitivity = -penalty * 0.2;
          score += priceSensitivity;
        }
      }
    }

    // Explicit profile preferences (user-controlled)
    if (profile != null) {
      String restaurant = normalizeText(item.restaurantName());
      if (restaurant != null && !restaurant.isBlank()) {
        Set<String> preferRestaurants = profile.preferRestaurants() == null ? Set.of() : profile.preferRestaurants();
        Set<String> avoidRestaurants = profile.avoidRestaurants() == null ? Set.of() : profile.avoidRestaurants();

        if (preferRestaurants.contains(restaurant)) {
          restaurantAffinity += 0.8;
          score += 0.8;
          why.add("Preferred restaurant (profile)");
        }
        if (avoidRestaurants.contains(restaurant)) {
          restaurantAffinity -= 1.2;
          score -= 1.2;
          why.add("Avoid restaurant (profile)");
        }
      }

      Set<String> preferTags = profile.preferTags() == null ? Set.of() : profile.preferTags();
      if (!preferTags.isEmpty()) {
        Set<String> matched = intersect(itemTags, preferTags);
        if (!matched.isEmpty()) {
          double delta = matched.size() * 0.6;
          tagAffinity += delta;
          score += delta;
          for (String t : matched) {
            why.add("Preferred tag: " + t);
          }
        }
      }
    }

    Decision.ScoreBreakdown breakdown = new Decision.ScoreBreakdown(
        base,
        budgetFit,
        mustTagMatch,
        avoidTagPenalty,
        queryMatch,
        restaurantAffinity,
        tagAffinity,
        priceSensitivity,
        score
    );

    return new Scored(item, score, why, breakdown);
  }

  private static String hashInput(DecideRequest request, int limit) {
    if (request == null) {
      return null;
    }

    String normalized = "budget=" + request.budget()
        + "|must=" + joinNormalized(request.mustHaveTags())
        + "|avoid=" + joinNormalized(request.avoidTags())
        + "|query=" + normalizeText(request.query())
        + "|limit=" + limit;

    return Hashing.sha256Hex(normalized);
  }

  private static String hashItems(List<Item> items) {
    if (items == null || items.isEmpty()) {
      return null;
    }

    String normalized = items.stream()
        .sorted(Comparator.comparing(Item::id, Comparator.nullsLast(Comparator.naturalOrder())))
        .map(i -> i.id() + ":" + (i.updatedAt() == null ? "" : i.updatedAt().toString()))
        .collect(Collectors.joining("|"));

    return Hashing.sha256Hex(normalized);
  }

  private static String hashPreference(UserPreference preference) {
    if (preference == null) {
      return null;
    }

    Map<String, Integer> tags = new TreeMap<>(preference.tagWeights() == null ? Map.of() : preference.tagWeights());
    Map<String, Integer> restaurants = new TreeMap<>(
        preference.restaurantWeights() == null ? Map.of() : preference.restaurantWeights()
    );

    UserPreference.PreferenceProfile profile = preference.effectiveProfile();
    String profileNormalized = "budgetMin=" + profile.budgetMin()
        + "|budgetMax=" + profile.budgetMax()
        + "|preferTags=" + joinSorted(profile.preferTags())
        + "|avoidTags=" + joinSorted(profile.avoidTags())
        + "|preferRestaurants=" + joinSorted(profile.preferRestaurants())
        + "|avoidRestaurants=" + joinSorted(profile.avoidRestaurants())
        + "|dietaryRestrictions=" + joinSorted(profile.dietaryRestrictions())
        + "|allergens=" + joinSorted(profile.allergens());

    String normalized = "tags=" + tags
        + "|restaurants=" + restaurants
        + "|pricePenalty=" + preference.pricePenalty()
        + "|updatedAt=" + (preference.updatedAt() == null ? "" : preference.updatedAt().toString())
        + "|schemaVersion=" + preference.schemaVersion()
        + "|profile=" + profileNormalized;

    return Hashing.sha256Hex(normalized);
  }

  private static Decision.UserPreferenceSnapshot snapshotPreference(UserPreference preference) {
    if (preference == null) {
      return null;
    }

    return new Decision.UserPreferenceSnapshot(
        preference.schemaVersion(),
        preference.tagWeights() == null ? Map.of() : preference.tagWeights(),
        preference.restaurantWeights() == null ? Map.of() : preference.restaurantWeights(),
        preference.pricePenalty(),
        preference.updatedAt()
    );
  }

  private static String joinNormalized(List<String> values) {
    if (values == null || values.isEmpty()) {
      return "";
    }

    return values.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .map(s -> s.toLowerCase(Locale.ROOT))
        .sorted()
        .collect(Collectors.joining(","));
  }

  private static Set<String> union(Set<String> a, Set<String> b) {
    if ((a == null || a.isEmpty()) && (b == null || b.isEmpty())) {
      return Set.of();
    }
    Set<String> out = new HashSet<>();
    if (a != null) out.addAll(a);
    if (b != null) out.addAll(b);
    return Set.copyOf(out);
  }

  private static Set<String> intersect(Set<String> a, Set<String> b) {
    if (a == null || a.isEmpty() || b == null || b.isEmpty()) {
      return Set.of();
    }
    Set<String> out = new HashSet<>(a);
    out.retainAll(b);
    return Set.copyOf(out);
  }

  private static String joinSorted(Set<String> values) {
    if (values == null || values.isEmpty()) {
      return "";
    }
    return new TreeSet<>(values).stream().collect(Collectors.joining(","));
  }

  private static int clampLimit(Integer limit) {
    if (limit == null) {
      return MAX_LIMIT;
    }
    return Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, limit));
  }

  private static Set<String> normalizeTagSet(List<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return Set.of();
    }
    return tags.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .map(s -> s.toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }

  private static String normalizeText(String s) {
    if (s == null) {
      return "";
    }
    return s.trim().toLowerCase(Locale.ROOT);
  }

  private static List<Double> softmax(List<Double> scores) {
    if (scores == null || scores.isEmpty()) {
      return List.of();
    }

    double max = scores.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    double sum = 0.0;
    double[] exp = new double[scores.size()];

    for (int i = 0; i < scores.size(); i++) {
      double v = Math.exp(scores.get(i) - max);
      exp[i] = v;
      sum += v;
    }

    if (sum == 0.0) {
      double uniform = 1.0 / scores.size();
      return scores.stream().map(ignored -> uniform).toList();
    }

    List<Double> out = new ArrayList<>(scores.size());
    for (double v : exp) {
      out.add(v / sum);
    }

    return out;
  }
}
