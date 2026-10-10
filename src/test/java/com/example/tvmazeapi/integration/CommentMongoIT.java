package com.example.tvmazeapi.integration;

import com.example.tvmazeapi.document.CommentDocument;
import com.example.tvmazeapi.repository.CommentRepository;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.MappingMongoEntityInformation;
import org.springframework.data.mongodb.repository.support.SimpleMongoRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "logging.level.org.mongodb.driver=OFF")
@EnabledIfSystemProperty(named = "atlas.it.enabled", matches = "true")
class CommentMongoIT {
    @Autowired private MongoTemplate mongoTemplate;

    private CommentRepository repository;
    private String collectionName;
    private String runToken;
    private Integer showId;
    private boolean collectionCreated;
    private final List<CommentDocument> acknowledgedDocuments = new ArrayList<>();

    @BeforeEach
    void prepareExclusiveCollection() {
        assertThat(mongoTemplate.getDb().getName()).isEqualTo("tvmaze");
        runToken = UUID.randomUUID().toString();
        collectionName = "comments_it_" + runToken.replace("-", "");
        showId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        var information = new MappingMongoEntityInformation<CommentDocument, String>(
                commentEntity(), collectionName);
        assertThat(information.getCollectionName()).isEqualTo(collectionName)
                .isNotIn("comments", "shows");
        repository = new IsolatedCommentRepository(information, mongoTemplate);

        // Never reuse or clear an existing collection: creation must succeed.
        mongoTemplate.createCollection(collectionName);
        collectionCreated = true;
    }

    @AfterEach
    void removeOnlyAcknowledgedOwnedDocuments() {
        if (collectionCreated) {
            for (CommentDocument document : acknowledgedDocuments) {
                Query ownedDocument = Query.query(Criteria.where("_id").is(document.id())
                        .and("showId").is(document.showId())
                        .and("comment").is(document.comment())
                        .and("rating").is(document.rating()));
                mongoTemplate.remove(ownedDocument, collectionName);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, 5})
    void insertsAndRetrievesExactCommentWithBsonTypes(int rating) {
        CommentDocument document = fixture(rating);
        assertThat(repository.findById(document.id())).isEmpty();
        assertThat(insertOwned(document)).isEqualTo(document);
        assertThat(repository.findById(document.id())).contains(document);

        Document stored = mongoTemplate.findOne(idQuery(document.id()), Document.class, collectionName);
        assertThat(stored).isNotNull();
        assertThat(stored.get("_id")).isInstanceOf(String.class).isEqualTo(document.id());
        assertThat(stored.get("showId")).isInstanceOf(Integer.class).isEqualTo(showId);
        assertThat(stored.get("comment")).isInstanceOf(String.class).isEqualTo(document.comment());
        assertThat(stored.get("rating")).isInstanceOf(Integer.class).isEqualTo(rating);
        assertThat(stored.get("_class")).isEqualTo(CommentDocument.class.getName());
    }

    @Test
    void identicalCommentCreationsHaveIndependentIdsForSameShow() {
        CommentDocument first = fixture(3);
        CommentDocument second = new CommentDocument(UUID.randomUUID().toString(), showId,
                first.comment(), first.rating());
        assertThat(second.id()).isNotEqualTo(first.id());
        insertOwned(first);
        insertOwned(second);

        assertThat(repository.findById(first.id())).contains(first);
        assertThat(repository.findById(second.id())).contains(second);
        Query ownPair = Query.query(Criteria.where("_id").in(first.id(), second.id())
                .and("showId").is(showId));
        assertThat(mongoTemplate.count(ownPair, collectionName)).isEqualTo(2);
    }

    @Test
    void duplicateInsertDoesNotOverwriteOriginal() {
        CommentDocument first = fixture(0);
        insertOwned(first);
        CommentDocument duplicate = new CommentDocument(first.id(), showId, "Different text", 5);

        assertThatThrownBy(() -> repository.insert(duplicate)).isInstanceOf(DuplicateKeyException.class);
        assertThat(repository.findById(first.id())).contains(first);
        assertThat(mongoTemplate.count(idQuery(first.id()), collectionName)).isEqualTo(1);
    }

    private CommentDocument insertOwned(CommentDocument document) {
        // Track only inserts that returned successfully; failed/ambiguous writes are not deleted.
        CommentDocument inserted = repository.insert(document);
        acknowledgedDocuments.add(document);
        return inserted;
    }

    private CommentDocument fixture(int rating) {
        return new CommentDocument(UUID.randomUUID().toString(), showId,
                "  <p>Niña 日本語 😀 & HTML</p>\n\t " + runToken + "  ", rating);
    }

    private Query idQuery(String id) {
        return Query.query(Criteria.where("_id").is(id));
    }

    @SuppressWarnings("unchecked")
    private MongoPersistentEntity<CommentDocument> commentEntity() {
        var entity = mongoTemplate.getConverter().getMappingContext()
                .getRequiredPersistentEntity(CommentDocument.class);
        assertThat(entity.getType()).isEqualTo(CommentDocument.class);
        return (MongoPersistentEntity<CommentDocument>) entity;
    }

    private static class IsolatedCommentRepository extends SimpleMongoRepository<CommentDocument, String>
            implements CommentRepository {
        IsolatedCommentRepository(MappingMongoEntityInformation<CommentDocument, String> information,
                                  MongoTemplate template) {
            super(information, template);
        }
    }
}
