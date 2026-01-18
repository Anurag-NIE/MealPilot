package com.mealpilot.api.decide;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;
import com.mealpilot.api.common.CursorCodec;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

@Service
public class MongoDecisionHistoryService implements DecisionHistoryService {

  private final ReactiveMongoTemplate mongoTemplate;

  public MongoDecisionHistoryService(ReactiveMongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Mono<DecisionPage> list(String userId, DecisionHistoryQuery query) {
    Query mongoQuery = new Query();

    List<Criteria> criteria = new ArrayList<>();
    criteria.add(Criteria.where("userId").is(userId));

    if (query.from() != null) {
      criteria.add(Criteria.where("createdAt").gte(query.from()));
    }

    if (query.to() != null) {
      criteria.add(Criteria.where("createdAt").lte(query.to()));
    }

    if (query.hasFeedback() != null) {
      if (Boolean.TRUE.equals(query.hasFeedback())) {
        criteria.add(Criteria.where("feedback").ne(null));
      } else {
        criteria.add(Criteria.where("feedback").is(null));
      }
    }

    if (query.feedbackStatus() != null) {
      criteria.add(Criteria.where("feedback.status").is(query.feedbackStatus().name()));
    }

    if (query.reasonCode() != null && !query.reasonCode().isBlank()) {
      criteria.add(Criteria.where("feedback.reasonCode").is(query.reasonCode()));
    }

    if (query.cursor() != null && !query.cursor().isBlank()) {
      Cursor cursor = decodeCursor(query.cursor());

      Criteria olderThanCursor = new Criteria().orOperator(
          Criteria.where("createdAt").lt(cursor.createdAt()),
          new Criteria().andOperator(
              Criteria.where("createdAt").is(cursor.createdAt()),
              Criteria.where("_id").lt(cursor.id())
          )
      );

      criteria.add(olderThanCursor);
    }

    mongoQuery.addCriteria(new Criteria().andOperator(criteria.toArray(Criteria[]::new)));

    mongoQuery.with(Sort.by(
        Sort.Order.desc("createdAt"),
        Sort.Order.desc("_id")
    ));

    int fetchLimit = query.limit() + 1;
    mongoQuery.limit(fetchLimit);

    return mongoTemplate.find(mongoQuery, Decision.class)
        .collectList()
        .map(all -> {
          if (all.size() <= query.limit()) {
            return new DecisionPage(all, null);
          }

          List<Decision> page = all.subList(0, query.limit());
          Decision last = page.get(page.size() - 1);
          String nextCursor = encodeCursor(last.createdAt(), last.id());
          return new DecisionPage(page, nextCursor);
        });
  }

  private record Cursor(Instant createdAt, ObjectId id) {}

  private static Cursor decodeCursor(String cursor) {
    CursorCodec.Cursor decoded = CursorCodec.decode(cursor);
    if (!ObjectId.isValid(decoded.id())) {
      // Keep error message stable.
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid cursor");
    }
    return new Cursor(decoded.createdAt(), new ObjectId(decoded.id()));
  }

  private static String encodeCursor(Instant createdAt, String idHex) {
    if (createdAt == null || idHex == null || !ObjectId.isValid(idHex)) {
      return null;
    }

    return CursorCodec.encode(createdAt, idHex);
  }
}
