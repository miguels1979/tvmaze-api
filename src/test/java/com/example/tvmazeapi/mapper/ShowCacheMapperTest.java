package com.example.tvmazeapi.mapper;

import com.example.tvmazeapi.document.CachedShowDocument;
import com.example.tvmazeapi.exception.ShowCacheException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShowCacheMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ShowCacheMapper mapper = new ShowCacheMapper(objectMapper);

    private static final String COMPLETE_SHOW = """
            {"id":1,"name":"Niña 日本語 😀","summary":"<p>HTML & original</p>",
             "network":null,"genres":["Drama","Comedy"],
             "extra":{"enabled":true,"values":[null,3,2.5,{"nested":"áéíóú"}]}}
            """;

    @Test
    void serializesCompleteShowWithoutModifyingInput() throws Exception {
        ObjectNode show = (ObjectNode) objectMapper.readTree(COMPLETE_SHOW);
        ObjectNode snapshot = show.deepCopy();

        CachedShowDocument document = mapper.toDocument(1, show);

        assertThat(document.id()).isEqualTo(1);
        assertThat(objectMapper.readTree(document.showJson())).isEqualTo(snapshot);
        assertThat(show).isEqualTo(snapshot);
    }

    @Test
    void reconstructsCompleteShowWithoutModifyingDocument() throws Exception {
        var document = new CachedShowDocument(1, COMPLETE_SHOW);

        ObjectNode show = mapper.toShow(1, document);

        assertThat(show).isEqualTo(objectMapper.readTree(COMPLETE_SHOW));
        assertThat(show.has("network")).isTrue();
        assertThat(show.path("network").isNull()).isTrue();
        assertThat(show.path("summary").textValue()).isEqualTo("<p>HTML & original</p>");
        assertThat(document.showJson()).isEqualTo(COMPLETE_SHOW);
    }

    @Test
    void roundTripPreservesStructureAndReturnsIndependentTree() throws Exception {
        ObjectNode original = (ObjectNode) objectMapper.readTree(COMPLETE_SHOW);
        ObjectNode restored = mapper.toShow(1, mapper.toDocument(1, original));

        assertThat(restored).isEqualTo(original).isNotSameAs(original);
        restored.withObject("extra").put("newField", "changed");
        assertThat(original.path("extra").has("newField")).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsInvalidRequestedIdsInBothDirections(Integer showId) {
        assertThatThrownBy(() -> mapper.toDocument(showId, objectMapper.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.toShow(showId, new CachedShowDocument(1, COMPLETE_SHOW)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullShow() {
        assertInvalidData(() -> mapper.toDocument(1, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"id\":null}", "{\"id\":2}", "{\"id\":0}",
            "{\"id\":-1}", "{\"id\":1.0}", "{\"id\":\"1\"}", "{\"id\":true}",
            "{\"id\":[]}", "{\"id\":{}}", "{\"id\":2147483648}"})
    void rejectsInvalidJsonIdsInBothDirections(String json) throws Exception {
        ObjectNode show = (ObjectNode) objectMapper.readTree(json);
        assertInvalidData(() -> mapper.toDocument(1, show));
        assertInvalidData(() -> mapper.toShow(1, new CachedShowDocument(1, json)));
    }

    @Test
    void rejectsNullDocument() {
        assertInvalidData(() -> mapper.toShow(1, null));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, 2})
    void rejectsInvalidStoredDocumentIds(Integer storedId) {
        assertInvalidData(() -> mapper.toShow(1, new CachedShowDocument(storedId, COMPLETE_SHOW)));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t\n"})
    void rejectsMissingOrBlankShowJson(String json) {
        assertInvalidData(() -> mapper.toShow(1, new CachedShowDocument(1, json)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"id\":}", "{\"id\":1} {\"id\":2}", "{\"id\":1} garbage"})
    void classifiesMalformedJsonAndPreservesJacksonCause(String json) {
        var error = assertThrows(ShowCacheException.class,
                () -> mapper.toShow(1, new CachedShowDocument(1, json)));
        assertThat(error.getReason()).isEqualTo(ShowCacheException.Reason.INVALID_DATA);
        assertThat(error.getCause()).isInstanceOf(JsonProcessingException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "[{\"id\":1}]", "1", "true", "\"show\""})
    void rejectsNonObjectJsonRoots(String json) {
        assertInvalidData(() -> mapper.toShow(1, new CachedShowDocument(1, json)));
    }

    @Test
    void classifiesSerializationFailureAndPreservesJacksonCause() {
        ObjectNode show = objectMapper.createObjectNode().put("id", 1);
        show.putPOJO("extra", new UnserializableValue());

        var error = assertThrows(ShowCacheException.class, () -> mapper.toDocument(1, show));

        assertThat(error.getReason()).isEqualTo(ShowCacheException.Reason.INVALID_DATA);
        assertThat(error.getCause()).isInstanceOf(JsonProcessingException.class);
        assertThat(show.path("id").intValue()).isEqualTo(1);
        assertThat(show.has("extra")).isTrue();
    }

    private void assertInvalidData(org.junit.jupiter.api.function.Executable operation) {
        var error = assertThrows(ShowCacheException.class, operation);
        assertThat(error.getReason()).isEqualTo(ShowCacheException.Reason.INVALID_DATA);
    }

    public static class UnserializableValue {
        public String getValue() {
            throw new IllegalStateException("Cannot serialize this test value");
        }
    }
}
