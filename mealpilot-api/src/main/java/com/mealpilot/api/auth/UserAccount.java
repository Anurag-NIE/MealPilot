package com.mealpilot.api.auth;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("users")
public record UserAccount(
    @Id String id,
    String username,
    String passwordHash,
    List<String> roles,
    Instant createdAt
) {}
