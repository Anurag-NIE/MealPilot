package com.mealpilot.api.items;

import com.mealpilot.api.common.CursorCodec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
public class MongoItemHistoryService implements ItemHistoryService {

  private final ReactiveMongoTemplate mongoTemplate;

  public MongoItemHistoryService(ReactiveMongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Mono<ItemPage> list(String userId, ItemHistoryQuery query) {
    Query mongoQuery = new Query();

    List<Criteria> criteria = new ArrayList<>();
    criteria.add(Criteria.where("userId").is(userId));

    if (query.active() != null) {
      criteria.add(Criteria.where("active").is(query.active()));
    }

    if (query.from() != null) {
      criteria.add(Criteria.where("createdAt").gte(query.from()));
    }

    if (query.to() != null) {
      criteria.add(Criteria.where("createdAt").lte(query.to()));
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

    return mongoTemplate.find(mongoQuery, Item.class)
        .collectList()
        .map(all -> {
          if (all.size() <= query.limit()) {
            return new ItemPage(all, null);
          }

          List<Item> page = all.subList(0, query.limit());
          Item last = page.get(page.size() - 1);
          String nextCursor = encodeCursor(last.createdAt(), last.id());
          return new ItemPage(page, nextCursor);
        });
  }

  private record Cursor(Instant createdAt, ObjectId id) {}

  private static Cursor decodeCursor(String cursor) {
    CursorCodec.Cursor decoded = CursorCodec.decode(cursor);
    if (!ObjectId.isValid(decoded.id())) {
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
