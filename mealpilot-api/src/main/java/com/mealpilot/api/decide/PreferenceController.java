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
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/preferences")
@Tag(name = "Preferences", description = "User preference profile (explicit constraints) and learned preference signals")
public class PreferenceController {

  private final UserPreferenceRepository userPreferenceRepository;

  public PreferenceController(UserPreferenceRepository userPreferenceRepository) {
    this.userPreferenceRepository = userPreferenceRepository;
  }

  @GetMapping
    @Operation(
      summary = "Get current preferences",
      description = "Returns the stored preferences document for the authenticated user. "
        + "Includes learned signals (weights/penalties) and the explicit user profile when present."
    )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Preferences document",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = UserPreference.class),
              examples = @ExampleObject(
                  name = "preferences_ok",
                  value = "{\n" +
                      "  \"userId\": \"user_123\",\n" +
                      "  \"schemaVersion\": 2,\n" +
                      "  \"profile\": {\n" +
                      "    \"budgetMin\": 150,\n" +
                      "    \"budgetMax\": 300,\n" +
                      "    \"preferTags\": [\"veg\"],\n" +
                      "    \"avoidTags\": [\"peanut\"],\n" +
                      "    \"preferRestaurants\": [],\n" +
                      "    \"avoidRestaurants\": [],\n" +
                      "    \"dietaryRestrictions\": [\"veg\"],\n" +
                      "    \"allergens\": [\"peanut\"],\n" +
                      "    \"notes\": \"Prefer lighter dinners\"\n" +
                      "  },\n" +
                      "  \"tagWeights\": {\"spicy\": 2},\n" +
                      "  \"restaurantWeights\": {\"Spice Hub\": 1},\n" +
                      "  \"pricePenalty\": 0,\n" +
                      "  \"updatedAt\": \"2026-01-17T12:00:00Z\"\n" +
                      "}"
              )
          )
      )
  })
  public Mono<UserPreference> get(@AuthenticationPrincipal Jwt jwt) {
    return userPreferenceRepository.findById(jwt.getSubject())
        .defaultIfEmpty(UserPreference.empty(jwt.getSubject()));
  }

  public record UpdateProfileRequest(
      @Schema(description = "Minimum budget bound (inclusive)", example = "150")
      @Min(value = 0, message = "budgetMin must be >= 0")
      @Max(value = 100000, message = "budgetMin must be <= 100000")
      Integer budgetMin,

      @Schema(description = "Maximum budget bound (inclusive)", example = "300")
      @Min(value = 0, message = "budgetMax must be >= 0")
      @Max(value = 100000, message = "budgetMax must be <= 100000")
      Integer budgetMax,

      @Schema(description = "Tags the user tends to prefer", example = "[\"comfort\",\"veg\"]")
      @Size(max = 50, message = "preferTags must have <= 50 entries")
      Set<@Size(max = 32, message = "tag must be <= 32 characters") String> preferTags,

      @Schema(description = "Tags the user tends to avoid", example = "[\"spicy\"]")
      @Size(max = 50, message = "avoidTags must have <= 50 entries")
      Set<@Size(max = 32, message = "tag must be <= 32 characters") String> avoidTags,

      @Schema(description = "Restaurants the user tends to prefer", example = "[\"Punjabi Dhaba\"]")
      @Size(max = 50, message = "preferRestaurants must have <= 50 entries")
      Set<@Size(max = 120, message = "restaurantName must be <= 120 characters") String> preferRestaurants,

      @Schema(description = "Restaurants the user tends to avoid", example = "[\"Fancy Place\"]")
      @Size(max = 50, message = "avoidRestaurants must have <= 50 entries")
      Set<@Size(max = 120, message = "restaurantName must be <= 120 characters") String> avoidRestaurants,

      @Schema(description = "Dietary constraints", example = "[\"veg\"]")
      @Size(max = 20, message = "dietaryRestrictions must have <= 20 entries")
      Set<@Size(max = 32, message = "restriction must be <= 32 characters") String> dietaryRestrictions,

      @Schema(description = "Allergens to avoid", example = "[\"peanut\"]")
      @Size(max = 50, message = "allergens must have <= 50 entries")
      Set<@Size(max = 32, message = "allergen must be <= 32 characters") String> allergens,

      @Schema(description = "Free-form notes", example = "Prefer lighter dinners on weekdays")
      @Size(max = 500, message = "notes must be <= 500 characters")
      String notes
  ) {}

  @PutMapping("/profile")
  @ResponseStatus(HttpStatus.OK)
    @Operation(
      summary = "Upsert explicit preference profile",
      description = "Sets the explicit preference profile for the authenticated user (budget bounds, tags, dietary/allergen constraints). "
        + "This does not erase learned preference signals." 
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
      required = true,
      description = "Profile update payload",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = UpdateProfileRequest.class),
        examples = @ExampleObject(
          name = "profile_update",
          value = "{\n" +
            "  \"budgetMin\": 150,\n" +
            "  \"budgetMax\": 300,\n" +
            "  \"preferTags\": [\"veg\"],\n" +
            "  \"avoidTags\": [\"peanut\"],\n" +
            "  \"dietaryRestrictions\": [\"veg\"],\n" +
            "  \"allergens\": [\"peanut\"],\n" +
            "  \"notes\": \"Weekday dinners\"\n" +
            "}"
        )
      )
    )
    @ApiResponses({
      @ApiResponse(
        responseCode = "200",
        description = "Updated preferences document",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserPreference.class))
      )
    })
  public Mono<UserPreference> putProfile(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody UpdateProfileRequest body
  ) {
    if (body.budgetMin() != null && body.budgetMax() != null && body.budgetMin() > body.budgetMax()) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "budgetMin must be <= budgetMax"));
    }

    UserPreference.PreferenceProfile profile = new UserPreference.PreferenceProfile(
        body.budgetMin(),
        body.budgetMax(),
        normalizeSet(body.preferTags()),
        normalizeSet(body.avoidTags()),
        normalizeSet(body.preferRestaurants()),
        normalizeSet(body.avoidRestaurants()),
        normalizeSet(body.dietaryRestrictions()),
        normalizeSet(body.allergens()),
        body.notes() == null ? null : body.notes().trim()
    );

    return userPreferenceRepository.findById(jwt.getSubject())
        .defaultIfEmpty(UserPreference.empty(jwt.getSubject()))
        .map(existing -> existing.withProfile(profile))
        .flatMap(userPreferenceRepository::save);
  }

  private static Set<String> normalizeSet(Set<String> values) {
    if (values == null || values.isEmpty()) {
      return Set.of();
    }

    return values.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .map(s -> s.toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }
}
