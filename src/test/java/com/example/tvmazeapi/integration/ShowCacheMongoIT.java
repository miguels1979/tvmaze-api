package com.example.tvmazeapi.integration;

import com.example.tvmazeapi.document.CachedShowDocument;
import com.example.tvmazeapi.mapper.ShowCacheMapper;
import com.example.tvmazeapi.repository.CachedShowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.MappingMongoEntityInformation;
import org.springframework.data.mongodb.repository.support.SimpleMongoRepository;

import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "logging.level.org.mongodb.driver=OFF")
@EnabledIfSystemProperty(named = "atlas.it.enabled", matches = "true")
class ShowCacheMongoIT {

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ShowCacheMapper mapper;

    private CachedShowRepository repository;
    private String collectionName;
    private String runToken;
    private Integer showId;
    private boolean collectionCreated;
    private boolean workersStopped = true;
    private final AtomicReference<String> acknowledgedJson = new AtomicReference<>();

    @BeforeEach
    void prepareExclusiveCollection() {
        assertThat(mongoTemplate.getDb().getName()).isEqualTo("tvmaze");
        runToken = UUID.randomUUID().toString();
        collectionName = "shows_it_" + runToken.replace("-", "");
        showId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);

        var information = new MappingMongoEntityInformation<CachedShowDocument, Integer>(
                cachedShowEntity(), collectionName);
        assertThat(information.getCollectionName()).isEqualTo(collectionName).isNotEqualTo("shows");
        repository = new IsolatedCachedShowRepository(information, mongoTemplate);

        // Creation must succeed; an existing collection is never reused or cleared.
        mongoTemplate.createCollection(collectionName);
        collectionCreated = true;
    }

    @AfterEach
    void removeOnlyAcknowledgedOwnedDocument() {
        String ownedJson = acknowledgedJson.get();
        if (collectionCreated && workersStopped && ownedJson != null) {
            Query ownedDocument = Query.query(Criteria.where("_id").is(showId)
                    .and("showJson").is(ownedJson));
            mongoTemplate.remove(ownedDocument, collectionName);
        }
    }

    @Test
    void insertsAndRetrievesById() throws Exception {
        CachedShowDocument document = fixture("Original");
        assertThat(repository.findById(showId)).isEmpty();

        assertThat(insertOwned(document)).isEqualTo(document);

        assertThat(repository.findById(showId)).contains(document);
    }

    @Test
    void storesOriginalIdAsBsonInteger() throws Exception {
        insertOwned(fixture("Original"));

        Document stored = rawDocument();

        assertThat(stored.get("_id")).isInstanceOf(Integer.class).isEqualTo(showId);
    }

    @Test
    void storesShowJsonAsBsonString() throws Exception {
        CachedShowDocument document = fixture("Original");
        insertOwned(document);

        assertThat(rawDocument().get("showJson")).isInstanceOf(String.class)
                .isEqualTo(document.showJson());
    }

    @Test
    void preservesCompleteJsonThroughRealPersistence() throws Exception {
        CachedShowDocument document = fixture("Original");
        ObjectNode original = mapper.toShow(showId, document);
        ObjectNode snapshot = original.deepCopy();
        insertOwned(mapper.toDocument(showId, original));

        ObjectNode restored = mapper.toShow(showId, repository.findById(showId).orElseThrow());

        assertThat(restored).isEqualTo(snapshot);
        assertThat(original).isEqualTo(snapshot);
    }

    @Test
    void rejectsDuplicateIdAndKeepsOneEntry() throws Exception {
        CachedShowDocument document = fixture("Original");
        insertOwned(document);

        assertThatThrownBy(() -> repository.insert(document)).isInstanceOf(DuplicateKeyException.class);

        assertThat(mongoTemplate.count(idQuery(), collectionName)).isEqualTo(1);
    }

    @Test
    void duplicateInsertDoesNotOverwriteFirstDocument() throws Exception {
        CachedShowDocument first = fixture("First");
        CachedShowDocument second = fixture("Second");
        insertOwned(first);

        assertThatThrownBy(() -> repository.insert(second)).isInstanceOf(DuplicateKeyException.class);

        assertThat(repository.findById(showId)).contains(first);
        assertThat(rawDocument().getString("showJson")).isEqualTo(first.showJson());
    }

    @Test
    void concurrentInsertsHaveOneWinnerAndOneDuplicate() throws Exception {
        CachedShowDocument first = fixture("First");
        CachedShowDocument second = fixture("Second");
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        workersStopped = false;
        try {
            var firstResult = executor.submit(() -> insertAfterBarrier(first, barrier));
            var secondResult = executor.submit(() -> insertAfterBarrier(second, barrier));
            boolean firstWon = firstResult.get(45, TimeUnit.SECONDS);
            boolean secondWon = secondResult.get(45, TimeUnit.SECONDS);

            assertThat(firstWon ^ secondWon).isTrue();
            CachedShowDocument expected = firstWon ? first : second;
            assertThat(repository.findById(showId)).contains(expected);
            assertThat(mongoTemplate.count(idQuery(), collectionName)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            // Cleanup is skipped if a timed-out worker might still be writing.
            workersStopped = executor.awaitTermination(10, TimeUnit.SECONDS);
            assertThat(workersStopped).as("Integration workers must finish before cleanup").isTrue();
        }
    }

    private boolean insertAfterBarrier(CachedShowDocument document, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            insertOwned(document);
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    private CachedShowDocument insertOwned(CachedShowDocument document) {
        CachedShowDocument inserted = repository.insert(document);
        acknowledgedJson.set(inserted.showJson());
        return inserted;
    }

    private CachedShowDocument fixture(String name) throws Exception {
        ObjectNode show = (ObjectNode) objectMapper.readTree("""
                {"summary":"<p>HTML & Niña 日本語 😀</p>","network":null,
                 "genres":["Drama","Comedy"],
                 "extra":{"enabled":true,"values":[null,3,2.5,{"nested":"áéíóú"}]}}
                """);
        show.put("id", showId);
        show.put("name", name);
        show.put("integrationRun", runToken);
        return mapper.toDocument(showId, show);
    }

    private Query idQuery() {
        return Query.query(Criteria.where("_id").is(showId));
    }

    private Document rawDocument() {
        Document stored = mongoTemplate.findOne(idQuery(), Document.class, collectionName);
        assertThat(stored).isNotNull();
        return stored;
    }

    @SuppressWarnings("unchecked")
    private MongoPersistentEntity<CachedShowDocument> cachedShowEntity() {
        var entity = mongoTemplate.getConverter().getMappingContext()
                .getRequiredPersistentEntity(CachedShowDocument.class);
        assertThat(entity.getType()).isEqualTo(CachedShowDocument.class);
        return (MongoPersistentEntity<CachedShowDocument>) entity;
    }

    private static class IsolatedCachedShowRepository
            extends SimpleMongoRepository<CachedShowDocument, Integer>
            implements CachedShowRepository {

        IsolatedCachedShowRepository(MappingMongoEntityInformation<CachedShowDocument, Integer> information,
                                    MongoTemplate template) {
            super(information, template);
        }
    }
}
