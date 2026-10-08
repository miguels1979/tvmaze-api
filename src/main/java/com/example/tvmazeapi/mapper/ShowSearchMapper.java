package com.example.tvmazeapi.mapper;

import com.example.tvmazeapi.exception.TvMazeClientException;
import com.example.tvmazeapi.dto.response.ShowSearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ShowSearchMapper {

    public ShowSearchResponse toResponse(ObjectNode show) {
        if (show == null) {
            throw invalidField("show");
        }

        JsonNode id = show.path("id");
        if (!id.isIntegralNumber() || !id.canConvertToInt() || id.intValue() <= 0) {
            throw invalidField("id");
        }

        String name = readNullableText(show, "name");
        if (name == null || name.isBlank()) {
            throw invalidField("name");
        }

        return new ShowSearchResponse(
                id.intValue(),
                name,
                resolveChannel(show),
                readNullableText(show, "summary"),
                readGenres(show)
        );
    }

    private String resolveChannel(ObjectNode show) {
        String networkName = readChannelName(show, "network");
        String webChannelName = readChannelName(show, "webChannel");

        if (networkName != null && !networkName.isBlank()) {
            return networkName;
        }
        return webChannelName != null && !webChannelName.isBlank()
                ? webChannelName : null;
    }

    private String readChannelName(ObjectNode show, String field) {
        JsonNode channel = show.path(field);
        if (channel.isMissingNode() || channel.isNull()) {
            return null;
        }
        if (!channel.isObject()) {
            throw invalidField(field);
        }
        return readNullableText(channel, "name");
    }

    private String readNullableText(JsonNode object, String field) {
        JsonNode value = object.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalidField(field);
        }
        return value.textValue();
    }

    private List<String> readGenres(ObjectNode show) {
        JsonNode genres = show.path("genres");
        if (genres.isMissingNode() || genres.isNull()) {
            return List.of();
        }
        if (!genres.isArray()) {
            throw invalidField("genres");
        }

        List<String> values = new ArrayList<>();
        for (JsonNode genre : genres) {
            if (!genre.isTextual()) {
                throw invalidField("genres[]");
            }
            values.add(genre.textValue());
        }
        return List.copyOf(values);
    }

    private TvMazeClientException invalidField(String field) {
        return new TvMazeClientException(
                TvMazeClientException.Reason.BAD_RESPONSE,
                "TVMaze returned an invalid field: " + field
        );
    }
}
