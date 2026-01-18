package com.mealpilot.api.items;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("items")
public record Item(
    @Id String id,
    String userId,
    String name,
    String restaurantName,
    List<String> tags,
    List<String> platformHints,
    Integer priceEstimate,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {}
