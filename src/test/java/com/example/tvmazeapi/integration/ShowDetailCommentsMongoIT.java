package com.example.tvmazeapi.integration;

import com.example.tvmazeapi.client.TvMazeClient;
import com.example.tvmazeapi.document.CachedShowDocument;
import com.example.tvmazeapi.document.CommentDocument;
import com.example.tvmazeapi.dto.response.CommentResponse;
import com.example.tvmazeapi.exception.CommentReadException;
import com.example.tvmazeapi.mapper.ShowCacheMapper;
import com.example.tvmazeapi.mapper.ShowDetailMapper;
import com.example.tvmazeapi.mapper.ShowSearchMapper;
import com.example.tvmazeapi.repository.CachedShowRepository;
import com.example.tvmazeapi.repository.CommentRepository;
import com.example.tvmazeapi.service.CommentQueryService;
import com.example.tvmazeapi.service.ShowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.framework.Advised;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.BasicMongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.data.util.TypeInformation;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "logging.level.org.mongodb.driver=OFF")
@EnabledIfSystemProperty(named = "atlas.it.enabled", matches = "true")
class ShowDetailCommentsMongoIT {
    @Autowired private MongoTemplate productionTemplate;
    @Autowired private ObjectMapper json;

    private MongoTemplate isolatedTemplate;
    private CachedShowRepository shows;
    private CommentRepository comments;
    private TvMazeClient client;
    private ShowService service;
    private String showsCollection;
    private String commentsCollection;
    private String runToken;
    private int showId;
    private boolean showsCreated;
    private boolean commentsCreated;
    private final List<CachedShowDocument> ownedShows = new ArrayList<>();
    private final List<CommentDocument> ownedComments = new ArrayList<>();

    @BeforeEach
    void composeRealServicesWithIsolatedRepositoryProxies() throws Exception {
        assertThat(productionTemplate.getDb().getName()).isEqualTo("tvmaze");
        runToken = UUID.randomUUID().toString();
        showsCollection = "shows_it_" + runToken.replace("-", "");
        commentsCollection = "comments_it_" + runToken.replace("-", "");
        showId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);

        var context = new IsolatedMappingContext(showsCollection, commentsCollection);
        context.setAutoIndexCreation(false);
        context.afterPropertiesSet();
        var converter = new MappingMongoConverter(
                new DefaultDbRefResolver(productionTemplate.getMongoDatabaseFactory()), context);
        converter.afterPropertiesSet();
        isolatedTemplate = new MongoTemplate(productionTemplate.getMongoDatabaseFactory(), converter);
        assertThat(isolatedTemplate.getCollectionName(CachedShowDocument.class)).isEqualTo(showsCollection);
        assertThat(isolatedTemplate.getCollectionName(CommentDocument.class)).isEqualTo(commentsCollection);
        assertThat(showsCollection).isNotIn("shows", "comments");
        assertThat(commentsCollection).isNotIn("shows", "comments");

        var factory = new MongoRepositoryFactory(isolatedTemplate);
        shows = factory.getRepository(CachedShowRepository.class);
        comments = factory.getRepository(CommentRepository.class);
        assertThat(AopUtils.isAopProxy(shows)).isTrue();
        assertThat(AopUtils.isAopProxy(comments)).isTrue();
        client = mock(TvMazeClient.class);
        var queryService = new CommentQueryService(comments);
        service = new ShowService(client, new ShowSearchMapper(), shows,
                new ShowCacheMapper(json), queryService, new ShowDetailMapper(json));
        assertThat(ReflectionTestUtils.getField(service, "cachedShowRepository")).isSameAs(shows);
        assertThat(ReflectionTestUtils.getField(service, "commentQueryService")).isSameAs(queryService);
        assertThat(ReflectionTestUtils.getField(queryService, "repository")).isSameAs(comments);

        // createCollection fails if the randomly named collection already exists.
        isolatedTemplate.createCollection(showsCollection);
        showsCreated = true;
        isolatedTemplate.createCollection(commentsCollection);
        commentsCreated = true;
    }

    @AfterEach
    void removeOnlyConfirmedOwnedDocuments() {
        try {
            if (commentsCreated) {
                for (var document : ownedComments) {
                    isolatedTemplate.remove(Query.query(Criteria.where("_id").is(document.id())
                            .and("showId").is(document.showId()).and("comment").is(document.comment())
                            .and("rating").is(document.rating())), commentsCollection);
                }
            }
        } finally {
            if (showsCreated) {
                for (var document : ownedShows) {
                    isolatedTemplate.remove(Query.query(Criteria.where("_id").is(document.id())
                            .and("showJson").is(document.showJson())), showsCollection);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cacheHitReadsRealCommentsAndPreservesOriginal(boolean withComments) throws Exception {
        var original = originalShow();
        var stored = insertShow(original);
        var documents = withComments ? insertRepeatedComments() : List.<CommentDocument>of();

        assertResponse(service.getShow(showId), original, documents);

        assertThat(shows.findById(showId)).contains(stored);
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cacheMissPersistsOriginalAndSecondRequestUsesCache(boolean withComments) throws Exception {
        var original = originalShow();
        var snapshot = original.deepCopy();
        var documents = withComments ? insertRepeatedComments() : List.<CommentDocument>of();
        when(client.getShow(showId)).thenReturn(original);

        var first = service.getShow(showId);
        var stored = confirmServiceInsertion(original);
        assertResponse(first, snapshot, documents);
        assertResponse(service.getShow(showId), snapshot, documents);

        assertThat(original).isEqualTo(snapshot);
        assertThat(json.readTree(stored.showJson()).has("comments")).isFalse();
        assertThat(shows.findById(showId)).contains(stored);
        verify(client).getShow(showId);
        verifyNoMoreInteractions(client);
    }

    @Test
    void providerCommentsAreReplacedOnlyInResponse() throws Exception {
        var original = originalShow();
        original.putObject("comments").put("provider", "Original " + runToken);
        var snapshot = original.deepCopy();
        var documents = insertRepeatedComments();
        when(client.getShow(showId)).thenReturn(original);

        var response = service.getShow(showId);
        var stored = confirmServiceInsertion(snapshot);
        assertResponse(response, snapshot, documents);
        assertThat(original).isEqualTo(snapshot);
        assertThat(json.readTree(stored.showJson())).isEqualTo(snapshot);
        verify(client).getShow(showId);
        verifyNoMoreInteractions(client);
    }

    @Test
    void newCommentAppearsOnNextCacheHitWithoutChangingCachedJson() throws Exception {
        var original = originalShow();
        var stored = insertShow(original);
        var first = insertComment("First", 4);
        assertResponse(service.getShow(showId), original, List.of(first));

        var second = insertComment("Second", 5);
        assertResponse(service.getShow(showId), original, List.of(first, second));

        assertThat(shows.findById(showId)).contains(stored);
        verifyNoInteractions(client);
    }

    @Test
    void corruptStoredCommentProducesInvalidDataWithoutPartialResponse() throws Exception {
        insertShow(originalShow());
        insertComment("Valid", 5);
        insertComment("Invalid rating", 6);

        assertThatThrownBy(() -> service.getShow(showId))
                .isInstanceOfSatisfying(CommentReadException.class,
                        error -> assertThat(error.getReason()).isEqualTo(CommentReadException.Reason.INVALID_DATA));
        verifyNoInteractions(client);
    }

    @Test
    void simulatedCommentReadFailurePreservesRealCacheInsertion() throws Exception {
        var original = originalShow();
        when(client.getShow(showId)).thenReturn(original);
        var cause = new DataAccessResourceFailureException("Simulated isolated read failure");
        // Inject only this failure at the real proxy boundary; do not change Atlas availability.
        ((Advised) comments).addAdvice(0, (MethodInterceptor) invocation -> {
            if (invocation.getMethod().getName().equals("findByShowIdIn")) {
                throw cause;
            }
            return invocation.proceed();
        });

        assertThatThrownBy(() -> service.getShow(showId))
                .isInstanceOfSatisfying(CommentReadException.class, error -> {
                    assertThat(error.getReason()).isEqualTo(CommentReadException.Reason.UNAVAILABLE);
                    assertThat(error.getCause()).isSameAs(cause);
                });
        var stored = confirmServiceInsertion(original);
        assertThat(shows.findById(showId)).contains(stored);
        verify(client).getShow(showId);
        verifyNoMoreInteractions(client);
    }

    private ObjectNode originalShow() throws Exception {
        var show = (ObjectNode) json.readTree("""
                {"name":"Serie 日本語 🎬","summary":"<p>Niña &amp; HTML</p>","ended":null,
                 "network":{"country":{"code":"US"}},"genres":["Drama","Comedy"],
                 "extra":{"values":[null,1,{"enabled":true,"nested":["á",3.5]}]}}
                """);
        show.put("id", showId);
        show.put("integrationOwner", runToken);
        return show;
    }

    private CachedShowDocument insertShow(ObjectNode original) {
        var document = new ShowCacheMapper(json).toDocument(showId, original);
        var inserted = shows.insert(document);
        ownedShows.add(document);
        assertThat(inserted).isEqualTo(document);
        return document;
    }

    private CachedShowDocument confirmServiceInsertion(ObjectNode original) throws Exception {
        var stored = shows.findById(showId).orElseThrow();
        // Register only after confirming the complete expected payload and unique ownership token.
        assertThat(json.readTree(stored.showJson())).isEqualTo(original);
        assertThat(original.path("integrationOwner").textValue()).isEqualTo(runToken);
        ownedShows.add(stored);
        return stored;
    }

    private CommentDocument insertComment(String text, int rating) {
        var document = new CommentDocument(UUID.randomUUID().toString(), showId,
                "  " + text + " 日本語 <b>🎬</b> " + runToken + "  ", rating);
        var inserted = comments.insert(document);
        ownedComments.add(document);
        assertThat(inserted).isEqualTo(document);
        return document;
    }

    private List<CommentDocument> insertRepeatedComments() {
        return List.of(insertComment("Repeated", 5), insertComment("Repeated", 5));
    }

    private void assertResponse(ObjectNode response, ObjectNode original, List<CommentDocument> documents) {
        assertThat(response).isNotSameAs(original);
        var expected = original.deepCopy();
        var publicComments = documents.stream().sorted(Comparator.comparing(CommentDocument::id))
                .map(document -> new CommentResponse(document.comment(), document.rating())).toList();
        expected.set("comments", json.valueToTree(publicComments));
        assertThat(response).isEqualTo(expected);
        assertThat(response.path("comments").isArray()).isTrue();
        for (var comment : response.path("comments")) {
            assertThat(comment.size()).isEqualTo(2);
            assertThat(comment.has("comment")).isTrue();
            assertThat(comment.has("rating")).isTrue();
        }
    }

    private static class IsolatedMappingContext extends MongoMappingContext {
        private final String showsCollection;
        private final String commentsCollection;

        IsolatedMappingContext(String showsCollection, String commentsCollection) {
            this.showsCollection = showsCollection;
            this.commentsCollection = commentsCollection;
        }

        @Override
        protected <T> BasicMongoPersistentEntity<T> createPersistentEntity(TypeInformation<T> information) {
            String collection;
            if (information.getType().equals(CachedShowDocument.class)) {
                collection = showsCollection;
            } else if (information.getType().equals(CommentDocument.class)) {
                collection = commentsCollection;
            } else {
                throw new IllegalArgumentException("Only isolated show and comment documents are allowed");
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
