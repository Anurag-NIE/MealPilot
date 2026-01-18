package com.mealpilot.api.decide;

import java.time.Instant;
import java.util.List;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;

public interface DecisionHistoryService {

  Mono<DecisionPage> list(String userId, DecisionHistoryQuery query);

  record DecisionHistoryQuery(
      int limit,
      @Nullable String cursor,
      @Nullable Instant from,
      @Nullable Instant to,
      @Nullable Boolean hasFeedback,
      @Nullable Decision.FeedbackStatus feedbackStatus,
      @Nullable String reasonCode
  ) {}

  record DecisionPage(
      List<Decision> items,
      @Nullable String nextCursor
  ) {}
}
