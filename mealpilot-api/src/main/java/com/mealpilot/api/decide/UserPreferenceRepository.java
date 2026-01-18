package com.mealpilot.api.decide;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface UserPreferenceRepository extends ReactiveCrudRepository<UserPreference, String> {
}
