package com.mealpilot.api.decide;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface DecisionEventRepository extends ReactiveCrudRepository<DecisionEvent, String> {}
