package com.mealpilot.api.ops;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mealpilot.migrations.enabled", havingValue = "true")
public class MongoSchemaMigrations implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(MongoSchemaMigrations.class);

  private final ReactiveMongoTemplate mongo;

  public MongoSchemaMigrations(ReactiveMongoTemplate mongo) {
    this.mongo = mongo;
  }

  @Override
  public void run(ApplicationArguments args) {
    // MongoDB is schema-less; these migrations are optional and safe to re-run.
    // They only backfill fields that help the app reason about document versions.

    long updated = mongo.updateMulti(
            new Query(new Criteria().orOperator(
                Criteria.where("schemaVersion").exists(false),
                Criteria.where("schemaVersion").is(null)
            )),
            Update.update("schemaVersion", 2),
            "user_preferences"
        )
        .map(r -> r.getModifiedCount())
        .onErrorResume(e -> {
          log.warn("UserPreference schemaVersion backfill failed", e);
          return reactor.core.publisher.Mono.just(0L);
        })
        .block(Duration.ofSeconds(30));

    log.info("Mongo migrations complete: user_preferences.schemaVersion backfilled on {} docs", updated);
  }
}
