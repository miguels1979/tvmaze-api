package com.example.tvmazeapi.integration;

import com.example.tvmazeapi.document.CommentDocument;
import com.example.tvmazeapi.repository.CommentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.BasicMongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.data.util.TypeInformation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "logging.level.org.mongodb.driver=OFF")
@EnabledIfSystemProperty(named = "atlas.it.enabled", matches = "true")
class CommentRepositoryQueryMongoIT {
    @Autowired private MongoTemplate productionTemplate;

    private MongoTemplate isolatedTemplate;
    private CommentRepository repository;
    private String collectionName;
    private String runToken;
    private boolean collectionCreated;
    private final List<CommentDocument> acknowledgedDocuments = new ArrayList<>();

    @BeforeEach
    void createIsolatedMappingAndRealRepositoryProxy() throws Exception {
        assertThat(productionTemplate.getDb().getName()).isEqualTo("tvmaze");
        assertThat(productionTemplate.getCollectionName(CommentDocument.class)).isEqualTo("comments");
        runToken = UUID.randomUUID().toString();
        collectionName = "comments_it_" + runToken.replace("-", "");

        var context = new IsolatedCommentMappingContext(collectionName);
        context.setAutoIndexCreation(false);
        context.afterPropertiesSet();
        var converter = new MappingMongoConverter(
                new DefaultDbRefResolver(productionTemplate.getMongoDatabaseFactory()), context);
        converter.afterPropertiesSet();
        isolatedTemplate = new MongoTemplate(productionTemplate.getMongoDatabaseFactory(), converter);
        assertThat(isolatedTemplate.getCollectionName(CommentDocument.class))
                .isEqualTo(collectionName).isNotIn("comments", "shows");

        // No repository implementation or @Query: Spring Data derives the method itself.
        repository = new MongoRepositoryFactory(isolatedTemplate).getRepository(CommentRepository.class);
        assertThat(AopUtils.isAopProxy(repository)).isTrue();
        isolatedTemplate.createCollection(collectionName);
        collectionCreated = true;
    }

    @AfterEach
    void removeOnlyAcknowledgedOwnedDocuments() {
        if (collectionCreated) {
            for (CommentDocument document : acknowledgedDocuments) {
                Query ownDocument = Query.query(Criteria.where("_id").is(document.id())
                        .and("showId").is(document.showId())
                        .and("comment").is(document.comment())
                        .and("rating").is(document.rating()));
                isolatedTemplate.remove(ownDocument, collectionName);
            }
        }
    }

    @Test
    void derivesInQueryForMultipleIdsWithoutLosingOrChangingDocuments() {
        int firstId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE - 3);
        int secondId = firstId + 1;
        int emptyId = firstId + 2;
        int excludedId = firstId + 3;
        var first = insertOwned(fixture(firstId, "Same content", 5));
        var anotherForFirst = insertOwned(fixture(firstId, "Same content", 5));
        var second = insertOwned(fixture(secondId, "Different content", 0));
        var excluded = insertOwned(fixture(excludedId, "Excluded", 3));

        List<CommentDocument> found = repository.findByShowIdIn(List.of(firstId, secondId, emptyId));
        assertThat(found).containsExactlyInAnyOrder(first, anotherForFirst, second);
        assertThat(found).noneMatch(document -> document.showId().equals(emptyId)
                || document.showId().equals(excludedId));
        assertThat(found).doesNotContain(excluded);
        for (CommentDocument document : acknowledgedDocuments) {
            assertThat(repository.findById(document.id())).contains(document);
        }
        assertThat(productionTemplate.getCollectionName(CommentDocument.class)).isEqualTo("comments");
    }

    private CommentDocument fixture(int showId, String text, int rating) {
        return new CommentDocument(UUID.randomUUID().toString(), showId,
                "  " + text + " 日本語 😀 " + runToken + "  ", rating);
    }

    private CommentDocument insertOwned(CommentDocument document) {
        CommentDocument inserted = repository.insert(document);
        acknowledgedDocuments.add(document);
        assertThat(inserted).isEqualTo(document);
        return inserted;
    }

    private static class IsolatedCommentMappingContext extends MongoMappingContext {
        private final String collection;

        IsolatedCommentMappingContext(String collection) {
            this.collection = collection;
        }

        @Override
        protected <T> BasicMongoPersistentEntity<T> createPersistentEntity(TypeInformation<T> information) {
            if (!CommentDocument.class.equals(information.getType())) {
                throw new IllegalArgumentException("Only CommentDocument is allowed in this test mapping");
            }
            return new BasicMongoPersistentEntity<T>(information) {
                @Override
                public String getCollection() {
                    return collection;
                }
            };
        }
    }
}
