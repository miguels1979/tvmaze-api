package com.example.tvmazeapi.service;

import com.example.tvmazeapi.exception.TvMazeClientException;
import com.example.tvmazeapi.exception.ShowNotFoundException;
import com.example.tvmazeapi.exception.ShowCacheException;
import com.example.tvmazeapi.document.CachedShowDocument;
import com.example.tvmazeapi.mapper.ShowCacheMapper;
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
import com.example.tvmazeapi.client.TvMazeClient;
import com.example.tvmazeapi.dto.external.TvMazeSearchResult;
import com.example.tvmazeapi.dto.response.ShowSearchResponse;
import com.example.tvmazeapi.mapper.ShowSearchMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowServiceTest {
    @Mock private TvMazeClient client;
    @Mock private ShowSearchMapper mapper;
    @Mock private CachedShowRepository repository;
    @Mock private ShowCacheMapper cacheMapper;
    @InjectMocks private ShowService service;

    @AfterEach
    void verifiesNoUnexpectedCacheInteractions() {
        verifyNoMoreInteractions(repository, cacheMapper);
    }

    @Test
    void stripsOuterSpacesCallsClientOnceAndMapsInOrder() {
        var first = JsonNodeFactory.instance.objectNode().put("id", 2);
        var second = JsonNodeFactory.instance.objectNode().put("id", 1);
        var firstResponse = new ShowSearchResponse(2, "First", null, null, List.of());
        var secondResponse = new ShowSearchResponse(1, "Second", null, null, List.of());
        when(client.searchShows("law & order")).thenReturn(List.of(
                new TvMazeSearchResult(first), new TvMazeSearchResult(second)));
        when(mapper.toResponse(first)).thenReturn(firstResponse);
        when(mapper.toResponse(second)).thenReturn(secondResponse);

        assertThat(service.search(" \t law & order \n")).containsExactly(firstResponse, secondResponse);
        var order = inOrder(client, mapper);
        order.verify(client).searchShows("law & order");
        order.verify(mapper).toResponse(first);
        order.verify(mapper).toResponse(second);
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
    void getsShowOnceAndReturnsSameUnmodifiedObject() throws Exception {
        ObjectNode show = (ObjectNode) new ObjectMapper().readTree("""
                {"id":1,"summary":"<p>HTML</p>","network":null,
                 "additional":{"values":[1,null,{"enabled":true}]}}
                """);
        ObjectNode snapshot = show.deepCopy();
        var document = new CachedShowDocument(1, show.toString());
        when(repository.findById(1)).thenReturn(Optional.empty());
        when(client.getShow(1)).thenReturn(show);
        when(cacheMapper.toDocument(1, show)).thenReturn(document);

        assertThat(service.getShow(1)).isSameAs(show).isEqualTo(snapshot);

        var order = inOrder(repository, client, cacheMapper);
        order.verify(repository).findById(1);
        order.verify(client).getShow(1);
        order.verify(cacheMapper).toDocument(1, show);
        order.verify(repository).insert(document);
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

    @Test
    void returnsCacheHitWithoutCallingProvider() throws Exception {
        ObjectNode show = (ObjectNode) new ObjectMapper().readTree("""
                {"id":1,"summary":"<p>Niña 日本語</p>","network":null,
                 "extra":{"values":[1,null,{"enabled":true}]}}
                """);
        ObjectNode snapshot = show.deepCopy();
        var document = new CachedShowDocument(1, show.toString());
        when(repository.findById(1)).thenReturn(Optional.of(document));
        when(cacheMapper.toShow(1, document)).thenReturn(show);

        assertThat(service.getShow(1)).isSameAs(show).isEqualTo(snapshot);

        var order = inOrder(repository, cacheMapper);
        order.verify(repository).findById(1);
        order.verify(cacheMapper).toShow(1, document);
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

        assertThat(service.getShow(1)).isSameAs(winnerShow);
        assertThat(show.path("name").textValue()).isEqualTo("Candidate");

        var order = inOrder(repository, client, cacheMapper);
        order.verify(repository).findById(1);
        order.verify(client).getShow(1);
        order.verify(cacheMapper).toDocument(1, show);
        order.verify(repository).insert(document);
        order.verify(repository).findById(1);
        order.verify(cacheMapper).toShow(1, winner);
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
