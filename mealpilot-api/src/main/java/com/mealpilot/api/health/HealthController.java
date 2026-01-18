package com.mealpilot.api.health;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class HealthController {

  @GetMapping("/api/health")
  public Mono<Map<String, Object>> health() {
    return Mono.just(
        Map.of(
            "status", "ok",
            "service", "mealpilot-api",
            "time", Instant.now().toString()
        )
    );
  }
}
