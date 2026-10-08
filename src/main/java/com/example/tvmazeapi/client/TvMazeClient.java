package com.example.tvmazeapi.client;

import com.example.tvmazeapi.dto.external.TvMazeSearchResult;

import com.example.tvmazeapi.exception.TvMazeClientException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;

@Component
public class TvMazeClient {

    private static final ParameterizedTypeReference<List<TvMazeSearchResult>> SEARCH_RESULT_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public TvMazeClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<TvMazeSearchResult> searchShows(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query must not be blank");
        }

        try {
            List<TvMazeSearchResult> results = restClient.get()
                    .uri("/search/shows?q={query}", query)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(SEARCH_RESULT_TYPE);

            if (results == null) {
                throw new TvMazeClientException(
                        TvMazeClientException.Reason.BAD_RESPONSE,
                        "TVMaze returned an empty response body"
                );
            }

            for (TvMazeSearchResult result : results) {
                if (result == null || result.show() == null) {
                    throw new TvMazeClientException(
                            TvMazeClientException.Reason.BAD_RESPONSE,
                            "TVMaze returned a search result without a show object"
                    );
                }
            }

            return List.copyOf(results);
        } catch (RestClientResponseException exception) {
            TvMazeClientException.Reason reason = exception.getStatusCode().value() == 429
                    ? TvMazeClientException.Reason.RATE_LIMITED
                    : TvMazeClientException.Reason.BAD_RESPONSE;
            throw new TvMazeClientException(
                    reason,
                    "TVMaze returned HTTP " + exception.getStatusCode().value(),
                    exception
            );
        } catch (ResourceAccessException exception) {
            boolean timeout = isTimeout(exception);
            throw new TvMazeClientException(
                    timeout ? TvMazeClientException.Reason.TIMEOUT
                            : TvMazeClientException.Reason.UNAVAILABLE,
                    timeout ? "TVMaze request timed out" : "Unable to connect to TVMaze",
                    exception
            );
        } catch (RestClientException | HttpMessageConversionException exception) {
            throw new TvMazeClientException(
                    TvMazeClientException.Reason.BAD_RESPONSE,
                    "Unable to interpret the TVMaze response",
                    exception
            );
        }
    }

    private boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
