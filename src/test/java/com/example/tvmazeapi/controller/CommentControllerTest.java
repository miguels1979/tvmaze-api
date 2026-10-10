package com.example.tvmazeapi.controller;

import com.example.tvmazeapi.dto.request.CreateCommentRequest;
import com.example.tvmazeapi.dto.response.CommentCreatedResponse;
import com.example.tvmazeapi.exception.CommentPersistenceException;
import com.example.tvmazeapi.exception.GlobalExceptionHandler;
import com.example.tvmazeapi.service.CommentService;
import com.example.tvmazeapi.service.ShowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({CommentController.class, ShowController.class})
@Import(GlobalExceptionHandler.class)
class CommentControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private CommentService service;
    @MockitoBean private ShowService showService;

    @ParameterizedTest
    @ValueSource(ints = {0, 3, 5})
    void createsCommentWithExactlyApprovedResponse(int rating) throws Exception {
        String text = "  Niña 😀  ";
        var request = new CreateCommentRequest(text, rating);
        when(service.create(1, request)).thenReturn(new CommentCreatedResponse("uuid", 1, "created"));
        mvc.perform(post("/api/shows/1/comments").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.id").value("uuid"))
                .andExpect(jsonPath("$.show_id").value(1))
                .andExpect(jsonPath("$.status").value("created"));
        verify(service).create(1, request);
        verifyNoMoreInteractions(service);
        verifyNoInteractions(showService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"  \""})
    void rejectsInvalidComment(String comment) throws Exception {
        invalidBody("{\"comment\":" + comment + ",\"rating\":3}",
                "Los datos del comentario no cumplen las restricciones requeridas.");
    }

    @Test
    void rejectsTooLongComment() throws Exception {
        invalidBody(mapper.writeValueAsString(new CreateCommentRequest("a".repeat(2001), 3)),
                "Los datos del comentario no cumplen las restricciones requeridas.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "-1", "6"})
    void rejectsInvalidRatingValue(String rating) throws Exception {
        invalidBody("{\"comment\":\"Good\",\"rating\":" + rating + "}",
                "Los datos del comentario no cumplen las restricciones requeridas.");
    }

    @Test
    void rejectsMissingRating() throws Exception {
        invalidBody("{\"comment\":\"Good\"}",
                "Los datos del comentario no cumplen las restricciones requeridas.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"4.0", "4.5", "\"5\"", "true", "false", "{}", "[]", "2147483648"})
    void rejectsIncompatibleRatingTokens(String rating) throws Exception {
        invalidBody("{\"comment\":\"Good\",\"rating\":" + rating + "}",
                "El cuerpo de la solicitud contiene datos no válidos.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "999999999999"})
    void rejectsInvalidShowId(String id) throws Exception {
        expectProblem(mvc.perform(post("/api/shows/{show_id}/comments", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Good\",\"rating\":3}")), 400);
        verifyNoInteractions(service, showService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "", "null"})
    void rejectsMalformedOrMissingBody(String body) throws Exception {
        invalidBody(body, "El cuerpo de la solicitud contiene datos no válidos.");
    }

    @Test
    void mapsPersistenceErrorWithoutSensitiveData() throws Exception {
        when(service.create(eq(1), any())).thenThrow(new CommentPersistenceException(
                "SECRET mongodb+srv://private diagnostic", new IllegalStateException("SECRET cause")));
        expectProblem(validPost(), 503)
                .andExpect(jsonPath("$.detail").value("No fue posible guardar el comentario temporalmente."));
        verify(service).create(1, new CreateCommentRequest("Good", 3));
    }

    @Test
    void mapsServiceConstraintViolationSafely() throws Exception {
        when(service.create(eq(1), any())).thenThrow(new ConstraintViolationException("SECRET", Set.of()));
        expectProblem(validPost(), 400)
                .andExpect(jsonPath("$.detail").value("Los datos de la solicitud no cumplen las restricciones requeridas."));
    }

    @Test
    void doesNotHideUnexpectedIllegalArgumentException() {
        when(service.create(eq(1), any())).thenThrow(new IllegalArgumentException("Programming error"));
        assertThatThrownBy(this::validPost).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "PATCH", "DELETE"})
    void rejectsUnsupportedMethods(String method) throws Exception {
        expectProblem(mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method),
                "/api/shows/1/comments")), 405).andExpect(header().string("Allow", "POST"));
        verifyNoInteractions(service, showService);
    }

    @Test
    void preservesSearchAndShowRoutes() throws Exception {
        when(showService.search("girls")).thenReturn(List.of());
        when(showService.getShow(1)).thenReturn(mapper.createObjectNode().put("id", 1));
        mvc.perform(get("/api/shows/search").param("search_query", "girls"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/shows/1")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        verify(showService).search("girls");
        verify(showService).getShow(1);
        verifyNoInteractions(service);
    }

    private ResultActions validPost() throws Exception {
        return mvc.perform(post("/api/shows/1/comments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"Good\",\"rating\":3}"));
    }

    private void invalidBody(String body, String detail) throws Exception {
        expectProblem(mvc.perform(post("/api/shows/1/comments").contentType(MediaType.APPLICATION_JSON)
                .content(body)), 400).andExpect(jsonPath("$.detail").value(detail));
        verifyNoInteractions(service, showService);
    }

    private ResultActions expectProblem(ResultActions result, int statusCode) throws Exception {
        return result.andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.cause").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(not(containsString("SECRET"))))
                .andExpect(content().string(not(containsString("mongodb+srv"))));
    }
}
