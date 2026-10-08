package com.example.tvmazeapi.client;

import com.example.tvmazeapi.dto.external.TvMazeSearchResult;

import com.example.tvmazeapi.config.TvMazeClientConfig;
import com.example.tvmazeapi.config.TvMazeProperties;
import com.example.tvmazeapi.exception.TvMazeClientException;
import com.example.tvmazeapi.exception.ShowNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TvMazeClientTest {
    private MockRestServiceServer server;
    private TvMazeClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        var properties = new TvMazeProperties(URI.create("https://api.tvmaze.com"),
                Duration.ofSeconds(3), Duration.ofSeconds(5));
        new TvMazeClientConfig().tvMazeRestClient(builder, properties);
        // Replace only the transport after applying the production configuration.
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TvMazeClient(builder.build());
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void sendsGetAndDeserializesNestedShowsInOrder() {
        expectQuery("girls");
        serverExpectationResponse("""
                [{"score":0.9,"show":{"id":2,"name":"First","extra":true}},
                 {"score":0.5,"show":{"id":1,"name":"Second"}}]
                """);
        var results = client.searchShows("girls");
        assertThat(results).extracting(result -> result.show().path("id").intValue())
                .containsExactly(2, 1);
        assertThat(results.get(0).show().path("name").textValue()).isEqualTo("First");
        assertThat(results.get(0).show().path("extra").booleanValue()).isTrue();
    }

    @Test
    void encodesSpacesAmpersandAndOtherSpecialCharacters() {
        server.expect(requestTo("https://api.tvmaze.com/search/shows?q=law%20%26%20order"))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(client.searchShows("law & order")).isEmpty();
    }

    @Test
    void encodesPlusPercentQuestionMarkHashAndUnicode() {
        server.expect(requestTo("https://api.tvmaze.com/search/shows?q=caf%C3%A9%20%2B%20100%25%3F%23"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(client.searchShows("café + 100%?#")).isEmpty();
    }

    @Test
    void acceptsEmptyResults() {
        expectQuery("girls");
        serverExpectationResponse("[]");
        assertThat(client.searchShows("girls")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 502, 503})
    void classifiesHttpErrorsAndPreservesCause(int status) {
        server.expect(requestTo("https://api.tvmaze.com/search/shows?q=girls"))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .body("internal upstream diagnostic").contentType(MediaType.TEXT_PLAIN));
        var error = assertThrows(TvMazeClientException.class, () -> client.searchShows("girls"));
        assertThat(error.getReason()).isEqualTo(status == 429
                ? TvMazeClientException.Reason.RATE_LIMITED : TvMazeClientException.Reason.BAD_RESPONSE);
        assertThat(error.getCause()).isInstanceOf(RestClientResponseException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{", "{}", "[null]", "[{}]", "[{\"show\":null}]",
            "[{\"show\":42}]", "[{\"show\":[]}]"})
    void rejectsInvalidBodies(String body) {
        expectQuery("girls");
        serverExpectationResponse(body);
        assertThatThrownBy(() -> client.searchShows("girls"))
                .isInstanceOfSatisfying(TvMazeClientException.class,
                        error -> assertThat(error.getReason()).isEqualTo(TvMazeClientException.Reason.BAD_RESPONSE));
    }

    @ParameterizedTest
    @MethodSource("timeouts")
    void classifiesDirectAndNestedTimeouts(IOException cause) {
        server.expect(requestTo("https://api.tvmaze.com/search/shows?q=girls"))
                .andRespond(request -> { throw cause; });
        assertThatThrownBy(() -> client.searchShows("girls"))
                .isInstanceOfSatisfying(TvMazeClientException.class, error -> {
                    assertThat(error.getReason()).isEqualTo(TvMazeClientException.Reason.TIMEOUT);
                    assertThat(error.getCause()).hasCause(cause);
                });
    }

    static Stream<IOException> timeouts() {
        return Stream.of(new HttpTimeoutException("read"), new HttpConnectTimeoutException("connect"),
                new SocketTimeoutException("socket"), new IOException("wrapper", new HttpTimeoutException("nested")));
    }

    @Test
    void classifiesConnectionFailure() {
        server.expect(requestTo("https://api.tvmaze.com/search/shows?q=girls"))
                .andRespond(request -> { throw new ConnectException("connection refused"); });
        assertThatThrownBy(() -> client.searchShows("girls"))
                .isInstanceOfSatisfying(TvMazeClientException.class,
                        error -> assertThat(error.getReason()).isEqualTo(TvMazeClientException.Reason.UNAVAILABLE));
    }

    @Test
    void rejectsInvalidQueriesBeforeSendingRequests() {
        for (String query : new String[]{null, "", "  "}) {
            assertThatThrownBy(() -> client.searchShows(query)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void getsShowAsCompleteObjectWithoutChangingProperties() throws Exception {
        String body = """
                {"id":1,"name":"Show","summary":"<p>Original HTML</p>","ended":null,
                 "network":{"id":2,"country":{"name":"United States","code":"US"}},
                 "genres":["Drama","Comedy"],"schedule":{"days":["Monday"],"time":"20:00"},
                 "additional":{"enabled":true,"values":[null,3,{"nested":"value"}]}}
                """;
        expectShow(1).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        ObjectNode result = client.getShow(1);

        assertThat(result).isEqualTo(new ObjectMapper().readTree(body));
        assertThat(result.path("summary").textValue()).isEqualTo("<p>Original HTML</p>");
        assertThat(result.has("ended")).isTrue();
        assertThat(result.path("ended").isNull()).isTrue();
    }

    @Test
    void convertsShow404ToNotFoundAndPreservesCause() {
        expectShow(1).andRespond(withStatus(HttpStatus.NOT_FOUND).body("upstream diagnostic"));
        var error = assertThrows(ShowNotFoundException.class, () -> client.getShow(1));
        assertThat(error.getCause()).isInstanceOfSatisfying(RestClientResponseException.class,
                cause -> assertThat(cause.getStatusCode().value()).isEqualTo(404));
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 502, 503, 504})
    void classifiesShowHttpErrors(int status) {
        expectShow(1).andRespond(withStatus(HttpStatus.valueOf(status)).body("upstream diagnostic"));
        var error = assertThrows(TvMazeClientException.class, () -> client.getShow(1));
        assertThat(error.getReason()).isEqualTo(status == 429
                ? TvMazeClientException.Reason.RATE_LIMITED : TvMazeClientException.Reason.BAD_RESPONSE);
        assertThat(error.getCause()).isInstanceOf(RestClientResponseException.class);
    }

    @ParameterizedTest
    @MethodSource("timeouts")
    void classifiesShowDirectAndNestedTimeouts(IOException cause) {
        expectShow(1).andRespond(request -> { throw cause; });
        var error = assertThrows(TvMazeClientException.class, () -> client.getShow(1));
        assertThat(error.getReason()).isEqualTo(TvMazeClientException.Reason.TIMEOUT);
        assertThat(error.getCause()).hasCause(cause);
    }

    @Test
    void classifiesShowConnectionFailure() {
        var cause = new ConnectException("connection refused");
        expectShow(1).andRespond(request -> { throw cause; });
        var error = assertThrows(TvMazeClientException.class, () -> client.getShow(1));
        assertThat(error.getReason()).isEqualTo(TvMazeClientException.Reason.UNAVAILABLE);
        assertThat(error.getCause()).hasCause(cause);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{", "[]", "[{\"id\":1}]", "42", "true", "\"show\""})
    void rejectsEmptyMalformedOrIncompatibleShowBodies(String body) {
        expectShow(1).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var error = assertThrows(TvMazeClientException.class, () -> client.getShow(1));
        assertThat(error.getReason()).isEqualTo(TvMazeClientException.Reason.BAD_RESPONSE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"id\":null}", "{\"id\":2}", "{\"id\":\"1\"}",
            "{\"id\":1.0}", "{\"id\":2147483648}", "{\"id\":true}", "{\"id\":[]}", "{\"id\":{}}"})
    void rejectsMissingMismatchedOrIncompatibleShowIds(String body) {
        expectShow(1).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var error = assertThrows(TvMazeClientException.class, () -> client.getShow(1));
        assertThat(error.getReason()).isEqualTo(TvMazeClientException.Reason.BAD_RESPONSE);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsInvalidShowArgumentsBeforeHttpRequest(Integer showId) {
        assertThatThrownBy(() -> client.getShow(showId)).isInstanceOf(IllegalArgumentException.class);
    }

    private org.springframework.test.web.client.ResponseActions expectShow(int showId) {
        return server.expect(requestTo("https://api.tvmaze.com/shows/" + showId))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Accept", "application/json"));
    }

    private org.springframework.test.web.client.ResponseActions pendingResponse;

    private void expectQuery(String query) {
        pendingResponse = server.expect(requestTo("https://api.tvmaze.com/search/shows?q=" + query))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Accept", "application/json"));
    }

    private void serverExpectationResponse(String body) {
        pendingResponse.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
