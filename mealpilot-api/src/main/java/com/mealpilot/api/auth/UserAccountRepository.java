package com.mealpilot.api.auth;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface UserAccountRepository extends ReactiveCrudRepository<UserAccount, String> {
  Mono<UserAccount> findByUsername(String username);
}
