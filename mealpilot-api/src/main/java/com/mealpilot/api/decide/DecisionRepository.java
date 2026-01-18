package com.mealpilot.api.decide;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface DecisionRepository extends ReactiveCrudRepository<Decision, String> {
  Flux<Decision> findAllByUserId(String userId);

  Flux<Decision> findAllByUserIdOrderByCreatedAtDesc(String userId);
}
