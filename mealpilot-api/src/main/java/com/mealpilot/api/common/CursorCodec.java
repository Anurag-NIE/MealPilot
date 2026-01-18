package com.mealpilot.api.common;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cursor format: base64url("<epochMillis>:<id>")
 */
public final class CursorCodec {

  private CursorCodec() {}

  public record Cursor(Instant createdAt, String id) {}

  public static Cursor decode(String cursor) {
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(cursor);
      String s = new String(decoded, StandardCharsets.UTF_8);
      String[] parts = s.split(":", 2);
      if (parts.length != 2) {
        throw new IllegalArgumentException("bad cursor format");
      }

      long epochMillis = Long.parseLong(parts[0]);
      String id = parts[1];
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("bad cursor id");
      }

      return new Cursor(Instant.ofEpochMilli(epochMillis), id);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid cursor");
    }
  }

  public static String encode(Instant createdAt, String id) {
    String raw = createdAt.toEpochMilli() + ":" + id;
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }
}
