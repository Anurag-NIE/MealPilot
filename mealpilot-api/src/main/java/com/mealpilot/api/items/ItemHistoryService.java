package com.mealpilot.api.items;

import java.time.Instant;
import java.util.List;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;

public interface ItemHistoryService {

  Mono<ItemPage> list(String userId, ItemHistoryQuery query);

  record ItemHistoryQuery(
      int limit,
      @Nullable String cursor,
      @Nullable Instant from,
      @Nullable Instant to,
      @Nullable Boolean active
  ) {}

  record ItemPage(
      List<Item> items,
      @Nullable String nextCursor
  ) {}
}
