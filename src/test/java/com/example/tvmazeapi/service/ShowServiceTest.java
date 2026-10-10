package com.example.tvmazeapi.service;

import com.example.tvmazeapi.exception.TvMazeClientException;
import com.example.tvmazeapi.exception.ShowNotFoundException;
import com.example.tvmazeapi.exception.ShowCacheException;
import com.example.tvmazeapi.document.CachedShowDocument;
import com.example.tvmazeapi.mapper.ShowCacheMapper;
import com.example.tvmazeapi.mapper.ShowDetailMapper;
import com.example.tvmazeapi.repository.CachedShowRepository;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.AfterEach;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import com.example.tvmazeapi.client.TvMazeClient;
import com.example.tvmazeapi.dto.external.TvMazeSearchResult;
import com.example.tvmazeapi.dto.response.ShowSearchResponse;
import com.example.tvmazeapi.dto.response.CommentResponse;
import com.example.tvmazeapi.exception.CommentReadException;
import com.example.tvmazeapi.mapper.ShowSearchMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowServiceTest {
    @Mock private TvMazeClient client;
    @Mock private ShowSearchMapper mapper;
    @Mock private CachedShowRepository repository;
    @Mock private ShowCacheMapper cacheMapper;
    @Mock private CommentQueryService commentQueryService;
    @Spy private ShowDetailMapper detailMapper = new ShowDetailMapper(new ObjectMapper());
    @InjectMocks private ShowService service;

    @AfterEach
    void verifiesNoUnexpectedCacheInteractions() {
        verifyNoMoreInteractions(repository, cacheMapper, commentQueryService, detailMapper);
    }

    @Test
    void stripsOuterSpacesCallsClientOnceAndMapsInOrder() {
        var first = JsonNodeFactory.instance.objectNode().put("id", 2);
        var second = JsonNodeFactory.instance.objectNode().put("id", 1);
        var firstResponse = new ShowSearchResponse(2, "First", null, null, List.of(), List.of());
        var secondResponse = new ShowSearchResponse(1, "Second", null, null, List.of(), List.of());
        when(client.searchShows("law & order")).thenReturn(List.of(
                new TvMazeSearchResult(first), new TvMazeSearchResult(second)));
        when(mapper.toResponse(first)).thenReturn(firstResponse);
        when(mapper.toResponse(second)).thenReturn(secondResponse);
        when(commentQueryService.findByShowIds(List.of(2, 1))).thenReturn(Map.of());

        assertThat(service.search(" \t law & order \n")).containsExactly(firstResponse, secondResponse);
        var order = inOrder(client, mapper, commentQueryService);
        order.verify(client).searchShows("law & order");
        order.verify(mapper).toResponse(first);
        order.verify(mapper).toResponse(second);
        order.verify(commentQueryService).findByShowIds(List.of(2, 1));
        verifyNoMoreInteractions(client, mapper);
    }

    @Test
    void returnsEmptyResultsWithoutMapping() {
        when(client.searchShows("girls")).thenReturn(List.of());
        assertThat(service.search("girls")).isEmpty();
        verify(client).searchShows("girls");
        verifyNoMoreInteractions(client);
        verifyNoInteractions(mapper);
    }

    @Test
    void propagatesClientErrorUnchanged() {
        var error = new TvMazeClientException(TvMazeClientException.Reason.TIMEOUT, "timeout");
        when(client.searchShows("girls")).thenThrow(error);
        assertThatThrownBy(() -> service.search("girls")).isSameAs(error);
        verify(client).searchShows("girls");
        verifyNoInteractions(mapper);
    }

    @Test
    void propagatesMapperErrorUnchanged() {
        var show = JsonNodeFactory.instance.objectNode();
        var error = new TvMazeClientException(TvMazeClientException.Reason.BAD_RESPONSE, "invalid");
        when(client.searchShows("girls")).thenReturn(List.of(new TvMazeSearchResult(show)));
        when(mapper.toResponse(show)).thenThrow(error);
        assertThatThrownBy(() -> service.search("girls")).isSameAs(error);
    }

    @Test
    void enrichesOriginalOrderAndRepeatedIdsWithoutChangingOriginalObjects() {
        var first = JsonNodeFactory.instance.objectNode().put("id", 2).put("extra", "original");
        var second = JsonNodeFactory.instance.objectNode().put("id", 1);
        var third = JsonNodeFactory.instance.objectNode().put("id", 2).put("name", "Repeated");
        var fourth = JsonNodeFactory.instance.objectNode().put("id", 3);
        var snapshots = List.of(first.deepCopy(), second.deepCopy(), third.deepCopy(), fourth.deepCopy());
        var firstResponse = new ShowSearchResponse(2, "First", "Network", "<p>HTML 日本語</p>",
                List.of("Drama", "Comedy"), List.of());
        var secondResponse = new ShowSearchResponse(1, "Second", null, null, List.of(), List.of());
        var thirdResponse = new ShowSearchResponse(2, "Repeated", null, "<p>Other</p>", List.of("Drama"), List.of());
        var fourthResponse = new ShowSearchResponse(3, "Without comments", null, null, List.of(), List.of());
        var originals = List.of(firstResponse, secondResponse, thirdResponse, fourthResponse);
        var commentsForTwo = List.of(new CommentResponse("  Excellent 😀  ", 5), new CommentResponse("Good", 4));
        var commentsForOne = List.of(new CommentResponse("Different", 0));
        when(client.searchShows("girls")).thenReturn(List.of(new TvMazeSearchResult(first),
                new TvMazeSearchResult(second), new TvMazeSearchResult(third), new TvMazeSearchResult(fourth)));
        when(mapper.toResponse(first)).thenReturn(firstResponse);
        when(mapper.toResponse(second)).thenReturn(secondResponse);
        when(mapper.toResponse(third)).thenReturn(thirdResponse);
        when(mapper.toResponse(fourth)).thenReturn(fourthResponse);
        when(commentQueryService.findByShowIds(List.of(2, 1, 2, 3)))
                .thenReturn(Map.of(2, commentsForTwo, 1, commentsForOne));

        var results = service.search("girls");
        assertThat(results).containsExactly(firstResponse.withComments(commentsForTwo),
                secondResponse.withComments(commentsForOne), thirdResponse.withComments(commentsForTwo), fourthResponse);
        for (int index = 0; index < originals.size(); index++) {
            var original = originals.get(index);
            var result = results.get(index);
            assertThat(result).isNotSameAs(original);
            assertThat(result.id()).isEqualTo(original.id());
            assertThat(result.name()).isEqualTo(original.name());
            assertThat(result.channel()).isEqualTo(original.channel());
            assertThat(result.summary()).isEqualTo(original.summary());
            assertThat(result.genres()).isEqualTo(original.genres());
            assertThat(original.comments()).isEmpty();
        }
        assertThat(List.of(first, second, third, fourth)).containsExactlyElementsOf(snapshots);
        var order = inOrder(client, mapper, commentQueryService);
        order.verify(client).searchShows("girls");
        order.verify(mapper).toResponse(first);
        order.verify(mapper).toResponse(second);
        order.verify(mapper).toResponse(third);
        order.verify(mapper).toResponse(fourth);
        order.verify(commentQueryService).findByShowIds(List.of(2, 1, 2, 3));
        verifyNoMoreInteractions(client, mapper);
    }

    @ParameterizedTest
    @EnumSource(CommentReadException.Reason.class)
    void propagatesCommentReadErrorsUnchanged(CommentReadException.Reason reason) {
        var show = JsonNodeFactory.instance.objectNode().put("id", 1);
        var response = new ShowSearchResponse(1, "Show", null, null, List.of(), List.of());
        var error = new CommentReadException(reason, "Internal diagnostic", new IllegalStateException("Cause"));
        when(client.searchShows("girls")).thenReturn(List.of(new TvMazeSearchResult(show)));
        when(mapper.toResponse(show)).thenReturn(response);
        when(commentQueryService.findByShowIds(List.of(1))).thenThrow(error);
        assertThatThrownBy(() -> service.search("girls")).isSameAs(error);
        verify(client).searchShows("girls");
        verify(mapper).toResponse(show);
        verify(commentQueryService).findByShowIds(List.of(1));
        assertThat(response.comments()).isEmpty();
    }

    @Test
    void validatesEveryResultBeforeQueryingComments() {
        var valid = JsonNodeFactory.instance.objectNode().put("id", 1);
        var invalid = JsonNodeFactory.instance.objectNode();
        when(client.searchShows("girls")).thenReturn(List.of(new TvMazeSearchResult(valid), new TvMazeSearchResult(invalid)));
        when(mapper.toResponse(valid)).thenReturn(new ShowSearchResponse(1, "Show", null, null, List.of(), List.of()));
        var error = new TvMazeClientException(TvMazeClientException.Reason.BAD_RESPONSE, "Invalid second show");
        when(mapper.toResponse(invalid)).thenThrow(error);
        assertThatThrownBy(() -> service.search("girls")).isSameAs(error);
        verifyNoInteractions(commentQueryService);
    }

    @ParameterizedTest
    @MethodSource("detailComments")
    void getsShowOncePersistsOriginalThenReturnsEnrichedCopy(List<CommentResponse> comments) throws Exception {
        ObjectNode show = (ObjectNode) new ObjectMapper().readTree("""
                {"id":1,"summary":"<p>HTML</p>","network":null,
                 "additional":{"values":[1,null,{"enabled":true}]}}
                """);
        ObjectNode snapshot = show.deepCopy();
        var document = new CachedShowDocument(1, show.toString());
        when(repository.findById(1)).thenReturn(Optional.empty());
        when(client.getShow(1)).thenReturn(show);
        when(cacheMapper.toDocument(1, show)).thenReturn(document);
        when(commentQueryService.findByShowIds(List.of(1)))
                .thenReturn(comments.isEmpty() ? Map.of() : Map.of(1, comments));

        assertEnrichedCopy(service.getShow(1), show, snapshot, comments);
        assertThat(document.showJson()).isEqualTo(snapshot.toString());
        assertThat(new ObjectMapper().readTree(document.showJson()).has("comments")).isFalse();

        var order = inOrder(repository, client, cacheMapper, commentQueryService, detailMapper);
        order.verify(repository).findById(1);
        order.verify(client).getShow(1);
        order.verify(cacheMapper).toDocument(1, show);
        order.verify(repository).insert(document);
        order.verify(commentQueryService).findByShowIds(List.of(1));
        order.verify(detailMapper).toResponse(show, comments);
        verifyNoMoreInteractions(client);
        verifyNoInteractions(mapper);
    }

    @Test
    void propagatesShowNotFoundUnchanged() {
        var error = new ShowNotFoundException(1);
        when(repository.findById(1)).thenReturn(Optional.empty());
        when(client.getShow(1)).thenThrow(error);
        assertThatThrownBy(() -> service.getShow(1)).isSameAs(error);
        verify(client).getShow(1);
        verify(repository).findById(1);
        verifyNoInteractions(cacheMapper);
        verifyNoMoreInteractions(client);
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @EnumSource(TvMazeClientException.Reason.class)
    void propagatesShowIntegrationErrorUnchanged(TvMazeClientException.Reason reason) {
        var error = new TvMazeClientException(reason, "upstream diagnostic");
        when(repository.findById(1)).thenReturn(Optional.empty());
        when(client.getShow(1)).thenThrow(error);
        assertThatThrownBy(() -> service.getShow(1)).isSameAs(error);
        verify(client).getShow(1);
        verify(repository).findById(1);
        verifyNoInteractions(cacheMapper);
        verifyNoMoreInteractions(client);
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsInvalidShowIdsWithoutCallingDependencies(Integer showId) {
        assertThatThrownBy(() -> service.getShow(showId)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(client, mapper, repository, cacheMapper);
    }

    @Test
    void rejectsInvalidArgumentsWithoutCallingDependencies() {
        for (String query : new String[]{null, "", " \t\n"}) {
            assertThatThrownBy(() -> service.search(query)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(client, mapper, repository, cacheMapper);
    }

    @ParameterizedTest
    @MethodSource("detailComments")
    void returnsEnrichedCacheHitWithoutCallingProvider(List<CommentResponse> comments) throws Exception {
        ObjectNode show = (ObjectNode) new ObjectMapper().readTree("""
                {"id":1,"summary":"<p>Niña 日本語</p>","network":null,
                 "extra":{"values":[1,null,{"enabled":true}]}}
                """);
        ObjectNode snapshot = show.deepCopy();
        var document = new CachedShowDocument(1, show.toString());
        when(repository.findById(1)).thenReturn(Optional.of(document));
        when(cacheMapper.toShow(1, document)).thenReturn(show);
        when(commentQueryService.findByShowIds(List.of(1)))
                .thenReturn(comments.isEmpty() ? Map.of() : Map.of(1, comments));

        assertEnrichedCopy(service.getShow(1), show, snapshot, comments);
        assertThat(document.showJson()).isEqualTo(snapshot.toString());

        var order = inOrder(repository, cacheMapper, commentQueryService, detailMapper);
        order.verify(repository).findById(1);
        order.verify(cacheMapper).toShow(1, document);
        order.verify(commentQueryService).findByShowIds(List.of(1));
        order.verify(detailMapper).toResponse(show, comments);
        verifyNoInteractions(client, mapper);
    }

    @Test
    void classifiesReadFailureWithoutCallingProvider() {
        var cause = new DataAccessResourceFailureException("read failure");
        when(repository.findById(1)).thenThrow(cause);

        assertUnavailable(() -> service.getShow(1), cause);

        verify(repository).findById(1);
        verifyNoInteractions(client, mapper, cacheMapper);
    }

    @Test
    void classifiesWriteFailureWithoutReturningSuccess() {
        var show = JsonNodeFactory.instance.objectNode().put("id", 1);
        var document = prepareMiss(show);
        var cause = new DataAccessResourceFailureException("write failure");
        when(repository.insert(document)).thenThrow(cause);

        assertUnavailable(() -> service.getShow(1), cause);

        verifyMissInteractions(show, document);
    }

    @Test
    void propagatesCorruptCachedDataUnchanged() {
        var document = new CachedShowDocument(1, "invalid JSON");
        var error = new ShowCacheException(ShowCacheException.Reason.INVALID_DATA, "invalid cache");
        when(repository.findById(1)).thenReturn(Optional.of(document));
        when(cacheMapper.toShow(1, document)).thenThrow(error);

        assertThatThrownBy(() -> service.getShow(1)).isSameAs(error);

        verify(repository).findById(1);
        verify(cacheMapper).toShow(1, document);
        verifyNoInteractions(client, mapper);
    }

    @Test
    void doesNotInsertWhenSerializationFails() {
        var show = JsonNodeFactory.instance.objectNode().put("id", 1);
        var error = new ShowCacheException(ShowCacheException.Reason.INVALID_DATA, "invalid data");
        when(repository.findById(1)).thenReturn(Optional.empty());
        when(client.getShow(1)).thenReturn(show);
        when(cacheMapper.toDocument(1, show)).thenThrow(error);

        assertThatThrownBy(() -> service.getShow(1)).isSameAs(error);

        verify(repository).findById(1);
        verify(client).getShow(1);
        verify(cacheMapper).toDocument(1, show);
        verifyNoMoreInteractions(client);
        verifyNoInteractions(mapper);
    }

    @Test
    void returnsConcurrentWinnerWithoutOverwritingIt() {
        var show = JsonNodeFactory.instance.objectNode().put("id", 1).put("name", "Candidate");
        var document = prepareMiss(show);
        var winnerShow = JsonNodeFactory.instance.objectNode().put("id", 1).put("name", "Winner");
        var winner = new CachedShowDocument(1, winnerShow.toString());
        when(repository.insert(document)).thenThrow(new DuplicateKeyException("duplicate ID"));
        when(repository.findById(1)).thenReturn(Optional.empty()).thenReturn(Optional.of(winner));
        when(cacheMapper.toShow(1, winner)).thenReturn(winnerShow);
        var comments = List.of(new CommentResponse("Winner comment", 5));
        when(commentQueryService.findByShowIds(List.of(1))).thenReturn(Map.of(1, comments));

        assertEnrichedCopy(service.getShow(1), winnerShow, winnerShow.deepCopy(), comments);
        assertThat(show.path("name").textValue()).isEqualTo("Candidate");
        assertThat(show.has("comments")).isFalse();
        assertThat(winner.showJson()).isEqualTo(winnerShow.toString());

        var order = inOrder(repository, client, cacheMapper, commentQueryService, detailMapper);
        order.verify(repository).findById(1);
        order.verify(client).getShow(1);
        order.verify(cacheMapper).toDocument(1, show);
        order.verify(repository).insert(document);
        order.verify(repository).findById(1);
        order.verify(cacheMapper).toShow(1, winner);
        order.verify(commentQueryService).findByShowIds(List.of(1));
        order.verify(detailMapper).toResponse(winnerShow, comments);
        verifyNoMoreInteractions(client);
        verifyNoInteractions(mapper);
    }

    @Test
    void classifiesWinnerReadFailure() {
        var show = JsonNodeFactory.instance.objectNode().put("id", 1);
        var document = prepareMiss(show);
        var cause = new DataAccessResourceFailureException("winner read failure");
        when(repository.insert(document)).thenThrow(new DuplicateKeyException("duplicate ID"));
        when(repository.findById(1)).thenReturn(Optional.empty()).thenThrow(cause);

        assertUnavailable(() -> service.getShow(1), cause);

        verifyDuplicateInteractions(show, document);
    }

    @Test
    void classifiesMissingWinnerWithoutRetrying() {
        var show = JsonNodeFactory.instance.objectNode().put("id", 1);
        var document = prepareMiss(show);
        var cause = new DuplicateKeyException("duplicate ID");
        when(repository.insert(document)).thenThrow(cause);

        assertUnavailable(() -> service.getShow(1), cause);

        verifyDuplicateInteractions(show, document);
    }

    @Test
    void propagatesCorruptWinnerUnchanged() {
        var show = JsonNodeFactory.instance.objectNode().put("id", 1);
        var document = prepareMiss(show);
        var winner = new CachedShowDocument(1, "invalid JSON");
        var error = new ShowCacheException(ShowCacheException.Reason.INVALID_DATA, "invalid winner");
        when(repository.insert(document)).thenThrow(new DuplicateKeyException("duplicate ID"));
        when(repository.findById(1)).thenReturn(Optional.empty()).thenReturn(Optional.of(winner));
        when(cacheMapper.toShow(1, winner)).thenThrow(error);

        assertThatThrownBy(() -> service.getShow(1)).isSameAs(error);

        verifyDuplicateInteractions(show, document);
        verify(cacheMapper).toShow(1, winner);
    }

    @ParameterizedTest
    @EnumSource(CommentReadException.Reason.class)
    void propagatesDetailCommentErrorsAfterInsertionWithoutRollback(CommentReadException.Reason reason) {
        var show = JsonNodeFactory.instance.objectNode().put("id", 1);
        var document = prepareMiss(show);
        var error = new CommentReadException(reason, "Internal diagnostic", new IllegalStateException("Cause"));
        when(commentQueryService.findByShowIds(List.of(1))).thenThrow(error);

        assertThatThrownBy(() -> service.getShow(1)).isSameAs(error);

        var order = inOrder(repository, client, cacheMapper, commentQueryService);
        order.verify(repository).findById(1);
        order.verify(client).getShow(1);
        order.verify(cacheMapper).toDocument(1, show);
        order.verify(repository).insert(document);
        order.verify(commentQueryService).findByShowIds(List.of(1));
        verifyNoInteractions(detailMapper);
        assertThat(show.has("comments")).isFalse();
        assertThat(document.showJson()).isEqualTo(show.toString());
    }

    @ParameterizedTest
    @EnumSource(CommentReadException.Reason.class)
    void propagatesDetailCommentErrorsOnCacheHit(CommentReadException.Reason reason) {
        var show = JsonNodeFactory.instance.objectNode().put("id", 1);
        var document = new CachedShowDocument(1, show.toString());
        var error = new CommentReadException(reason, "Internal diagnostic");
        when(repository.findById(1)).thenReturn(Optional.of(document));
        when(cacheMapper.toShow(1, document)).thenReturn(show);
        when(commentQueryService.findByShowIds(List.of(1))).thenThrow(error);

        assertThatThrownBy(() -> service.getShow(1)).isSameAs(error);

        verify(repository).findById(1);
        verify(cacheMapper).toShow(1, document);
        verify(commentQueryService).findByShowIds(List.of(1));
        verifyNoInteractions(client, detailMapper);
        assertThat(show.has("comments")).isFalse();
    }

    @Test
    void replacesProviderCommentsOnlyInResponseAndPersistsOriginal() {
        var show = JsonNodeFactory.instance.objectNode().put("id", 1).put("comments", "Provider value");
        var snapshot = show.deepCopy();
        var document = prepareMiss(show);
        var repeated = new CommentResponse("Same text", 5);
        var comments = List.of(repeated, repeated, new CommentResponse("Last", 0));
        when(commentQueryService.findByShowIds(List.of(1))).thenReturn(Map.of(1, comments));

        assertEnrichedCopy(service.getShow(1), show, snapshot, comments);

        verifyMissInteractions(show, document);
        verify(commentQueryService).findByShowIds(List.of(1));
        verify(detailMapper).toResponse(show, comments);
        assertThat(document.showJson()).isEqualTo(snapshot.toString());
    }

    private static Stream<List<CommentResponse>> detailComments() {
        return Stream.of(List.of(), List.of(new CommentResponse("Second", 4), new CommentResponse("First", 5)));
    }

    private void assertEnrichedCopy(ObjectNode response, ObjectNode original, ObjectNode snapshot,
                                    List<CommentResponse> comments) {
        assertThat(response).isNotSameAs(original);
        assertThat(original).isEqualTo(snapshot);
        assertThat(response.get("comments")).isEqualTo(new ObjectMapper().valueToTree(comments));
        var withoutComments = response.deepCopy();
        withoutComments.remove("comments");
        var expectedOriginalFields = snapshot.deepCopy();
        expectedOriginalFields.remove("comments");
        assertThat(withoutComments).isEqualTo(expectedOriginalFields);
    }

    private CachedShowDocument prepareMiss(ObjectNode show) {
        var document = new CachedShowDocument(1, show.toString());
        when(repository.findById(1)).thenReturn(Optional.empty());
        when(client.getShow(1)).thenReturn(show);
        when(cacheMapper.toDocument(1, show)).thenReturn(document);
        return document;
    }

    private void verifyMissInteractions(ObjectNode show, CachedShowDocument document) {
        verify(repository).findById(1);
        verify(client).getShow(1);
        verify(cacheMapper).toDocument(1, show);
        verify(repository).insert(document);
        verifyNoMoreInteractions(client);
        verifyNoInteractions(mapper);
    }

    private void verifyDuplicateInteractions(ObjectNode show, CachedShowDocument document) {
        verify(repository, times(2)).findById(1);
        verify(client).getShow(1);
        verify(cacheMapper).toDocument(1, show);
        verify(repository).insert(document);
        verifyNoMoreInteractions(client);
        verifyNoInteractions(mapper);
    }

    private void assertUnavailable(org.junit.jupiter.api.function.Executable operation, Throwable cause) {
        var error = org.junit.jupiter.api.Assertions.assertThrows(ShowCacheException.class, operation);
        assertThat(error.getReason()).isEqualTo(ShowCacheException.Reason.UNAVAILABLE);
        assertThat(error.getCause()).isSameAs(cause);
    }
}
