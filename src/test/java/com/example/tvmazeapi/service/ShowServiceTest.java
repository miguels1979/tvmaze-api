package com.example.tvmazeapi.service;

import com.example.tvmazeapi.exception.TvMazeClientException;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowServiceTest {
    @Mock private TvMazeClient client;
    @Mock private ShowSearchMapper mapper;
    @InjectMocks private ShowService service;

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
    void rejectsInvalidArgumentsWithoutCallingDependencies() {
        for (String query : new String[]{null, "", " \t\n"}) {
            assertThatThrownBy(() -> service.search(query)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(client, mapper);
    }
}
