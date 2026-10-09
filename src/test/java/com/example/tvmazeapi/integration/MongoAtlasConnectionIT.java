package com.example.tvmazeapi.integration;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "atlas.it.enabled", matches = "true")
class MongoAtlasConnectionIT {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void connectsToTvmazeDatabaseAndRespondsToPing() {
        assertThat(mongoTemplate.getDb().getName()).isEqualTo("tvmaze");

        Document response = mongoTemplate.executeCommand(new Document("ping", 1));
        Number ok = (Number) response.get("ok");
        assertThat(ok).isNotNull();
        assertThat(ok.doubleValue()).isEqualTo(1);
    }
}
