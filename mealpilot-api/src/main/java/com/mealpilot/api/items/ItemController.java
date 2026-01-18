package com.mealpilot.api.items;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/items")
public class ItemController {

  private final ItemRepository repo;
  private final ItemHistoryService itemHistoryService;

  public ItemController(ItemRepository repo, ItemHistoryService itemHistoryService) {
    this.repo = repo;
    this.itemHistoryService = itemHistoryService;
  }

  @GetMapping
  public Mono<ResponseEntity<List<Item>>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "limit", required = false) Integer limit,
      @RequestParam(name = "cursor", required = false) String cursor,
      @RequestParam(name = "from", required = false) String from,
      @RequestParam(name = "to", required = false) String to,
      @RequestParam(name = "active", required = false) Boolean active
  ) {
    int safeLimit = clamp(limit, 1, 200, 100);
    Boolean safeActive = (active == null) ? Boolean.TRUE : active;

    Instant fromInstant = parseInstantOrNull(from, "from");
    Instant toInstant = parseInstantOrNull(to, "to");
    if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be <= to"));
    }

    ItemHistoryService.ItemHistoryQuery q = new ItemHistoryService.ItemHistoryQuery(
        safeLimit,
        cursor,
        fromInstant,
        toInstant,
      safeActive
    );

    return itemHistoryService.list(jwt.getSubject(), q)
        .map(page -> {
          ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
          if (page.nextCursor() != null && !page.nextCursor().isBlank()) {
            builder.header("X-Next-Cursor", page.nextCursor());
          }
          return builder.body(page.items());
        });
  }

  public record CreateItemRequest(
      @NotBlank(message = "name is required")
      @Size(min = 2, max = 120, message = "name must be 2-120 characters")
      String name,

      @Size(max = 120, message = "restaurantName must be <= 120 characters")
      String restaurantName,

      @Size(max = 20, message = "tags must have <= 20 entries")
      List<@NotBlank(message = "tag cannot be blank") @Size(max = 32, message = "tag must be <= 32 characters") String> tags,

      @Size(max = 10, message = "platformHints must have <= 10 entries")
      List<@NotBlank(message = "platformHint cannot be blank") @Size(max = 32, message = "platformHint must be <= 32 characters") String> platformHints,

      @Min(value = 0, message = "priceEstimate must be >= 0")
      @Max(value = 100000, message = "priceEstimate must be <= 100000")
      Integer priceEstimate
  ) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<Item> create(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateItemRequest body
  ) {
    Instant now = Instant.now();
    Item item = new Item(
        null,
      jwt.getSubject(),
        body.name().trim(),
        body.restaurantName() == null ? null : body.restaurantName().trim(),
        body.tags() == null ? List.of() : body.tags(),
        normalizePlatformHints(body.platformHints()),
        body.priceEstimate(),
        true,
        now,
        now
    );

    return repo.save(item);
  }

  public record UpdateItemRequest(
      String name,
      String restaurantName,
      List<String> tags,
      List<String> platformHints,
      Integer priceEstimate,
      Boolean active
  ) {}

  @PatchMapping("/{id}")
  public Mono<Item> update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String id,
      @RequestBody UpdateItemRequest body
  ) {
    return repo.findById(id)
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found")))
        .flatMap(existing -> {
          if (!existing.userId().equals(jwt.getSubject())) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "not your item"));
          }

          Instant now = Instant.now();
          Item updated = new Item(
              existing.id(),
              existing.userId(),
              body.name() == null ? existing.name() : body.name().trim(),
              body.restaurantName() == null ? existing.restaurantName() : body.restaurantName().trim(),
              body.tags() == null ? existing.tags() : body.tags(),
              body.platformHints() == null ? existing.platformHints() : normalizePlatformHints(body.platformHints()),
              body.priceEstimate() == null ? existing.priceEstimate() : body.priceEstimate(),
              body.active() == null ? existing.active() : body.active(),
              existing.createdAt(),
              now
          );

          return repo.save(updated);
        });
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public Mono<Void> delete(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String id
  ) {
    return repo.findById(id)
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found")))
        .flatMap(existing -> {
          if (!existing.userId().equals(jwt.getSubject())) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "not your item"));
          }

          Item deleted = new Item(
              existing.id(),
              existing.userId(),
              existing.name(),
              existing.restaurantName(),
              existing.tags(),
              existing.platformHints(),
              existing.priceEstimate(),
              false,
              existing.createdAt(),
              Instant.now()
          );

          return repo.save(deleted).then();
        });
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

  private static List<String> normalizePlatformHints(List<String> platformHints) {
    if (platformHints == null || platformHints.isEmpty()) {
      return List.of();
    }
    return platformHints.stream()
        .filter(s -> s != null && !s.isBlank())
        .map(s -> s.trim().toLowerCase(Locale.ROOT))
        .distinct()
        .toList();
  }
}
