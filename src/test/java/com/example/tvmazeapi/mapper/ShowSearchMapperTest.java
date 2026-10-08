package com.example.tvmazeapi.mapper;

import com.example.tvmazeapi.exception.TvMazeClientException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class ShowSearchMapperTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ShowSearchMapper mapper = new ShowSearchMapper();

    @Test
    void extractsFiveFieldsAndPreservesHtmlAndGenreOrder() throws Exception {
        var response = mapper.toResponse(show("""
                {"id":1,"name":"Girls","network":{"name":"HBO"},
                 "webChannel":{"name":"Streaming"},"summary":"<p>Original & text</p>",
                 "genres":["Drama","Comedy"],"extra":"ignored"}
                """));
        assertThat(response.id()).isEqualTo(1);
        assertThat(response.name()).isEqualTo("Girls");
        assertThat(response.channel()).isEqualTo("HBO");
        assertThat(response.summary()).isEqualTo("<p>Original & text</p>");
        assertThat(response.genres()).containsExactly("Drama", "Comedy");
        assertThat(json.valueToTree(response).size()).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"name\":null}", "{\"name\":\" \"}"})
    void fallsBackToWebChannel(String network) throws Exception {
        var input = show("{\"id\":1,\"name\":\"Girls\",\"network\":" + network
                + ",\"webChannel\":{\"name\":\"Netflix\"}}");
        assertThat(mapper.toResponse(input).channel()).isEqualTo("Netflix");
    }

    @Test
    void fallsBackWhenNetworkIsMissing() throws Exception {
        assertThat(mapper.toResponse(show("""
                {"id":1,"name":"Girls","webChannel":{"name":"Netflix"}}
                """)).channel()).isEqualTo("Netflix");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"id\":1,\"name\":\"Girls\"}",
            "{\"id\":1,\"name\":\"Girls\",\"network\":null,\"webChannel\":null,\"summary\":null,\"genres\":null}",
            "{\"id\":1,\"name\":\"Girls\",\"network\":{},\"webChannel\":{},\"genres\":[]}"
    })
    void normalizesOptionalMissingNullAndEmptyFields(String input) throws Exception {
        var response = mapper.toResponse(show(input));
        assertThat(response.channel()).isNull();
        assertThat(response.summary()).isNull();
        assertThat(response.genres()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"id\":0,\"name\":\"A\"}", "{\"id\":1.5,\"name\":\"A\"}",
            "{\"id\":2147483648,\"name\":\"A\"}", "{\"id\":\"1\",\"name\":\"A\"}",
            "{\"id\":1,\"name\":null}", "{\"id\":1,\"name\":\" \"}",
            "{\"id\":1,\"name\":42}", "{\"id\":1,\"name\":\"A\",\"summary\":{}}",
            "{\"id\":1,\"name\":\"A\",\"network\":12}",
            "{\"id\":1,\"name\":\"A\",\"network\":{\"name\":5}}",
            "{\"id\":1,\"name\":\"A\",\"network\":{\"name\":\"HBO\"},\"webChannel\":[]}",
            "{\"id\":1,\"name\":\"A\",\"genres\":{}}",
            "{\"id\":1,\"name\":\"A\",\"genres\":[1]}",
            "{\"id\":1,\"name\":\"A\",\"genres\":[null]}"
    })
    void rejectsIncompatibleFields(String input) throws Exception {
        ObjectNode node = show(input);
        assertThatThrownBy(() -> mapper.toResponse(node))
                .isInstanceOfSatisfying(TvMazeClientException.class,
                        error -> assertThat(error.getReason()).isEqualTo(TvMazeClientException.Reason.BAD_RESPONSE));
    }

    @Test
    void rejectsNullShow() {
        assertThatThrownBy(() -> mapper.toResponse(null)).isInstanceOf(TvMazeClientException.class);
    }

    private ObjectNode show(String value) throws Exception {
        return (ObjectNode) json.readTree(value);
    }
}
