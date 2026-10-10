package com.example.tvmazeapi.controller;

import com.example.tvmazeapi.dto.response.ShowSearchResponse;
import com.example.tvmazeapi.dto.response.CommentResponse;
import com.example.tvmazeapi.exception.CommentReadException;

import com.example.tvmazeapi.exception.GlobalExceptionHandler;
import com.example.tvmazeapi.exception.ShowCacheException;
import com.example.tvmazeapi.exception.ShowNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.example.tvmazeapi.exception.TvMazeClientException;
import com.example.tvmazeapi.service.ShowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShowController.class)
@Import(GlobalExceptionHandler.class)
class ShowControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private ShowService service;

    @Test
    void returnsExactlySixFieldsIncludingNullValuesAndEmptyComments() throws Exception {
        when(service.search("girls")).thenReturn(List.of(
                new ShowSearchResponse(1, "Girls", "HBO", "<p>HTML</p>", List.of("Drama"), List.of()),
                new ShowSearchResponse(2, "Other", null, null, List.of(), List.of())));
        mvc.perform(get("/api/shows/search").param("search_query", "girls"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]", aMapWithSize(6)))
                .andExpect(jsonPath("$[0].comments", empty()))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Girls"))
                .andExpect(jsonPath("$[0].channel").value("HBO"))
                .andExpect(jsonPath("$[0].summary").value("<p>HTML</p>"))
                .andExpect(jsonPath("$[0].genres").value(contains("Drama")))
                .andExpect(jsonPath("$[1]", aMapWithSize(6)))
                .andExpect(jsonPath("$[1].comments", empty()))
                .andExpect(jsonPath("$[1]", hasKey("channel")))
                .andExpect(jsonPath("$[1].channel").value(nullValue()))
                .andExpect(jsonPath("$[1]", hasKey("summary")))
                .andExpect(jsonPath("$[1].summary").value(nullValue()))
                .andExpect(jsonPath("$[1].genres", empty()));
        verify(service).search("girls");
    }

    @Test
    void returnsCompleteShowWithOriginalJsonStructure() throws Exception {
        ObjectNode show = (ObjectNode) objectMapper.readTree("""
                {"id":1,"name":"Show","summary":"<p>HTML</p>","ended":null,
                 "network":{"country":{"code":"US"}},"genres":["Drama","Comedy"],
                 "additional":{"values":[null,1,{"enabled":true}]}}
                """);
        ObjectNode snapshot = show.deepCopy();
        when(service.getShow(1)).thenReturn(show);

        var result = mvc.perform(get("/api/shows/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())).isEqualTo(snapshot);
        assertThat(show).isEqualTo(snapshot);
        verify(service).getShow(1);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "2147483648", "-2147483649"})
    void rejectsShowIdsThatCannotConvertToInteger(String showId) throws Exception {
        mvc.perform(get("/api/shows/{show_id}", showId))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("El parámetro show_id debe ser un entero válido."))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(not(containsString("NumberFormatException"))));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveShowIds(int showId) throws Exception {
        mvc.perform(get("/api/shows/{show_id}", showId))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("El parámetro show_id debe ser mayor que cero."))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
        verifyNoInteractions(service);
    }

    @Test
    void returnsShowNotFoundWithoutLeakingInternals() throws Exception {
        when(service.getShow(1)).thenThrow(new ShowNotFoundException(1,
                new IllegalStateException("SECRET credential=abc INTERNAL diagnostic")));
        mvc.perform(get("/api/shows/1"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No se encontró la serie solicitada."))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(not(containsString("SECRET"))))
                .andExpect(content().string(not(containsString("INTERNAL"))))
                .andExpect(content().string(not(containsString("credential"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("TVMaze show not found"))));
        verify(service).getShow(1);
    }

    @ParameterizedTest
    @EnumSource(TvMazeClientException.Reason.class)
    void mapsShowIntegrationErrorsWithoutLeakingInternals(TvMazeClientException.Reason reason) throws Exception {
        when(service.getShow(1)).thenThrow(new TvMazeClientException(reason,
                "SECRET upstream body credential=abc", new IllegalStateException("INTERNAL stack detail")));
        int expectedStatus = switch (reason) {
            case BAD_RESPONSE -> 502;
            case RATE_LIMITED, UNAVAILABLE -> 503;
            case TIMEOUT -> 504;
        };
        mvc.perform(get("/api/shows/1"))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.detail", not(emptyOrNullString())))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(not(containsString("SECRET"))))
                .andExpect(content().string(not(containsString("INTERNAL"))))
                .andExpect(content().string(not(containsString("credential"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
        verify(service).getShow(1);
    }

    @ParameterizedTest
    @EnumSource(ShowCacheException.Reason.class)
    void mapsCacheErrorsWithoutLeakingInternals(ShowCacheException.Reason reason) throws Exception {
        String privateDiagnostic = "TECHNICAL mongodb+srv://private_user:private_password@internal.example/tvmaze"
                + " stored JSON: {\"secretField\":\"privatePayload\"}";
        when(service.getShow(1)).thenThrow(new ShowCacheException(reason, privateDiagnostic,
                new IllegalStateException("INTERNAL cause details")));
        int expectedStatus = reason == ShowCacheException.Reason.UNAVAILABLE ? 503 : 500;
        String expectedDetail = reason == ShowCacheException.Reason.UNAVAILABLE
                ? "El almacenamiento de series no está disponible temporalmente."
                : "No fue posible recuperar correctamente la serie almacenada.";
        String expectedTitle = reason == ShowCacheException.Reason.UNAVAILABLE
                ? "Service Unavailable" : "Internal Server Error";

        mvc.perform(get("/api/shows/1"))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.detail").value(expectedDetail))
                .andExpect(jsonPath("$.title").value(expectedTitle))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.instance").value("/api/shows/1"))
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(content().string(not(containsString("TECHNICAL"))))
                .andExpect(content().string(not(containsString("mongodb+srv"))))
                .andExpect(content().string(not(containsString("private_user"))))
                .andExpect(content().string(not(containsString("private_password"))))
                .andExpect(content().string(not(containsString("internal.example"))))
                .andExpect(content().string(not(containsString("secretField"))))
                .andExpect(content().string(not(containsString("privatePayload"))))
                .andExpect(content().string(not(containsString("INTERNAL"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.cause").doesNotExist());
        verify(service).getShow(1);
        verifyNoMoreInteractions(service);
    }

    @Test
    void routesSearchLiteralToSearchInsteadOfShowId() throws Exception {
        when(service.search("girls")).thenReturn(List.of());
        mvc.perform(get("/api/shows/search").param("search_query", "girls"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).search("girls");
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @EnumSource(CommentReadException.Reason.class)
    void mapsCommentReadErrorsWithoutPartialResultsOrSensitiveData(CommentReadException.Reason reason)
            throws Exception {
        String diagnostic = "TECHNICAL mongodb+srv://private_user:private_password@internal.example/tvmaze"
                + " document: {\"internalComment\":\"privatePayload\"}";
        var cause = new IllegalStateException("INTERNAL stack detail");
        var exception = new CommentReadException(reason, diagnostic, cause);
        when(service.search("girls")).thenThrow(exception);
        int expectedStatus = reason == CommentReadException.Reason.UNAVAILABLE ? 503 : 500;
        String expectedTitle = reason == CommentReadException.Reason.UNAVAILABLE
                ? "Service Unavailable" : "Internal Server Error";
        String expectedDetail = reason == CommentReadException.Reason.UNAVAILABLE
                ? "Los comentarios no están disponibles temporalmente."
                : "No fue posible recuperar correctamente los comentarios almacenados.";

        mvc.perform(get("/api/shows/search").param("search_query", "girls"))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value(expectedTitle))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.detail").value(expectedDetail))
                .andExpect(jsonPath("$.instance").value("/api/shows/search"))
                .andExpect(jsonPath("$.comments").doesNotExist())
                .andExpect(jsonPath("$.shows").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.cause").doesNotExist())
                .andExpect(content().string(not(containsString("TECHNICAL"))))
                .andExpect(content().string(not(containsString("mongodb+srv"))))
                .andExpect(content().string(not(containsString("private_user"))))
                .andExpect(content().string(not(containsString("private_password"))))
                .andExpect(content().string(not(containsString("internal.example"))))
                .andExpect(content().string(not(containsString("internalComment"))))
                .andExpect(content().string(not(containsString("privatePayload"))))
                .andExpect(content().string(not(containsString("INTERNAL"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
        assertThat(exception.getCause()).isSameAs(cause);
        verify(service).search("girls");
        verifyNoMoreInteractions(service);
    }

    @Test
    void returnsSearchWithCommentsAndExactlySixFields() throws Exception {
        when(service.search("girls")).thenReturn(List.of(new ShowSearchResponse(
                1, "Girls", null, "<p>Original HTML</p>", List.of("Drama"),
                List.of(new CommentResponse("Excellent", 5), new CommentResponse("Good", 4)))));
        mvc.perform(get("/api/shows/search").param("search_query", "girls"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0]", aMapWithSize(6)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Girls"))
                .andExpect(jsonPath("$[0].channel").value(nullValue()))
                .andExpect(jsonPath("$[0].summary").value("<p>Original HTML</p>"))
                .andExpect(jsonPath("$[0].genres").value(contains("Drama")))
                .andExpect(jsonPath("$[0].comments", hasSize(2)))
                .andExpect(jsonPath("$[0].comments[0]", aMapWithSize(2)))
                .andExpect(jsonPath("$[0].comments[0].comment").value("Excellent"))
                .andExpect(jsonPath("$[0].comments[0].rating").value(5))
                .andExpect(jsonPath("$[0].comments[1]", aMapWithSize(2)))
                .andExpect(jsonPath("$[0].comments[1].comment").value("Good"))
                .andExpect(jsonPath("$[0].comments[1].rating").value(4));
        verify(service).search("girls");
        verifyNoMoreInteractions(service);
    }

    @Test
    void returnsEmptyArray() throws Exception {
        when(service.search("girls")).thenReturn(List.of());
        mvc.perform(get("/api/shows/search").param("search_query", "girls"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void rejectsMissingQuery() throws Exception {
        mvc.perform(get("/api/shows/search"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " \t\n"})
    void rejectsBlankQueries(String query) throws Exception {
        mvc.perform(get("/api/shows/search").param("search_query", query))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @EnumSource(TvMazeClientException.Reason.class)
    void mapsIntegrationErrorsWithoutLeakingInternals(TvMazeClientException.Reason reason) throws Exception {
        var exception = new TvMazeClientException(reason, "SECRET upstream body credential=abc",
                new IllegalStateException("INTERNAL stack detail"));
        when(service.search("girls")).thenThrow(exception);
        int expectedStatus = switch (reason) {
            case BAD_RESPONSE -> 502;
            case RATE_LIMITED, UNAVAILABLE -> 503;
            case TIMEOUT -> 504;
        };
        mvc.perform(get("/api/shows/search").param("search_query", "girls"))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.detail", not(emptyOrNullString())))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString("SECRET"))))
                .andExpect(content().string(not(containsString("INTERNAL"))))
                .andExpect(content().string(not(containsString("credential"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }
}
