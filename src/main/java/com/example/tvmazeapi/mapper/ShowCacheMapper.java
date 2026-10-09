package com.example.tvmazeapi.mapper;

import com.example.tvmazeapi.document.CachedShowDocument;
import com.example.tvmazeapi.exception.ShowCacheException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class ShowCacheMapper {

    private final ObjectMapper objectMapper;

    public ShowCacheMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CachedShowDocument toDocument(Integer showId, ObjectNode show) {
        validateRequestedId(showId);
        validateShowId(showId, show);

        try {
            return new CachedShowDocument(showId, objectMapper.writeValueAsString(show));
        } catch (JsonProcessingException exception) {
            throw new ShowCacheException(ShowCacheException.Reason.INVALID_DATA,
                    "Unable to serialize the show for the cache", exception);
        }
    }

    public ObjectNode toShow(Integer showId, CachedShowDocument document) {
        validateRequestedId(showId);
        if (document == null || !showId.equals(document.id())) {
            throw invalidData("Cached document has an invalid or unexpected ID");
        }
        if (document.showJson() == null || document.showJson().isBlank()) {
            throw invalidData("Cached document has no show JSON");
        }

        try {
            JsonNode show = objectMapper.readerFor(JsonNode.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(document.showJson());
            if (!(show instanceof ObjectNode object)) {
                throw invalidData("Cached show JSON must be an object");
            }
            validateShowId(showId, object);
            return object;
        } catch (JsonProcessingException exception) {
            throw new ShowCacheException(ShowCacheException.Reason.INVALID_DATA,
                    "Unable to deserialize the cached show", exception);
        }
    }

    private void validateRequestedId(Integer showId) {
        if (showId == null || showId <= 0) {
            throw new IllegalArgumentException("Show ID must be a positive integer");
        }
    }

    private void validateShowId(Integer showId, ObjectNode show) {
        if (show == null) {
            throw invalidData("Show must not be null");
        }
        JsonNode id = show.path("id");
        if (!id.isIntegralNumber() || !id.canConvertToInt() || id.intValue() != showId) {
            throw invalidData("Show has an invalid or unexpected ID");
        }
    }

    private ShowCacheException invalidData(String message) {
        return new ShowCacheException(ShowCacheException.Reason.INVALID_DATA, message);
    }
}
