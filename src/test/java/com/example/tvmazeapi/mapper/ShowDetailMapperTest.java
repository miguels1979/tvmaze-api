package com.example.tvmazeapi.mapper;

import com.example.tvmazeapi.dto.response.CommentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ShowDetailMapperTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ShowDetailMapper mapper = new ShowDetailMapper(json);

    @Test
    void addsTwoCommentsWithExactlyThePublicFieldsInReceivedOrder() {
        var comments = List.of(new CommentResponse("Segundo", 4), new CommentResponse("Primero", 5));
        var response = mapper.toResponse(json.createObjectNode().put("id", 1), comments);

        assertThat(response.get("comments")).isInstanceOf(ArrayNode.class);
        assertThat(response.get("comments").size()).isEqualTo(2);
        for (int i = 0; i < comments.size(); i++) {
            var node = response.get("comments").get(i);
            List<String> fields = new ArrayList<>();
            node.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrder("comment", "rating");
            assertThat(node.get("comment").textValue()).isEqualTo(comments.get(i).comment());
            assertThat(node.get("rating").intValue()).isEqualTo(comments.get(i).rating());
        }
    }

    @Test
    void producesAnEmptyArrayWhenThereAreNoComments() {
        var response = mapper.toResponse(json.createObjectNode().put("id", 1), List.of());
        assertThat(response.get("comments")).isEqualTo(json.createArrayNode());
    }

    @Test
    void preservesTheCompleteOriginalStructureAndDoesNotMutateTheInput() throws Exception {
        ObjectNode original = (ObjectNode) json.readTree("""
                {"id":1,"name":"Serie 日本語","summary":"<p>¡Qué serie! 🎬</p>",
                 "network":{"name":"Canal","country":{"code":"ES"}},
                 "genres":["Drama","Comedia"],"schedule":{"days":["Monday"]},
                 "ended":null,"additional":{"values":[null,{"flag":true},3.5]}}
                """);
        ObjectNode snapshot = original.deepCopy();

        ObjectNode response = mapper.toResponse(original, List.of(new CommentResponse("  日本語 <b>🎬</b>  ", 5)));
        assertThat(response).isNotSameAs(original);
        assertThat(original).isEqualTo(snapshot);
        ObjectNode withoutComments = response.deepCopy();
        withoutComments.remove("comments");
        assertThat(withoutComments).isEqualTo(snapshot);
        assertThat(response.path("comments").get(0).path("comment").textValue())
                .isEqualTo("  日本語 <b>🎬</b>  ");
    }

    @Test
    void copiesNestedObjectsAndArraysIndependently() throws Exception {
        ObjectNode original = (ObjectNode) json.readTree("""
                {"network":{"name":"Original"},"values":[{"name":"Nested"},null]}
                """);
        ObjectNode snapshot = original.deepCopy();
        ObjectNode response = mapper.toResponse(original, List.of());

        ((ObjectNode) response.get("network")).put("name", "Changed");
        ((ObjectNode) response.get("values").get(0)).put("name", "Changed");
        ((ArrayNode) response.get("values")).add(42);
        assertThat(original).isEqualTo(snapshot);
        ((ObjectNode) original.get("network")).put("extra", true);
        assertThat(response.get("network").has("extra")).isFalse();
    }

    @Test
    void replacesProviderCommentsOnlyInTheResponse() throws Exception {
        ObjectNode original = (ObjectNode) json.readTree("""
                {"id":1,"comments":{"provider":"original"}}
                """);
        ObjectNode snapshot = original.deepCopy();
        ObjectNode response = mapper.toResponse(original, List.of(new CommentResponse("Local", 3)));
        assertThat(original).isEqualTo(snapshot);
        assertThat(response.get("comments")).isEqualTo(json.valueToTree(List.of(new CommentResponse("Local", 3))));
        assertThat(response.get("id")).isEqualTo(original.get("id"));
    }

    @Test
    void preservesRepeatedCommentsAndTheInputList() {
        var repeated = new CommentResponse("Mismo texto", 5);
        var comments = new ArrayList<>(List.of(repeated, repeated, new CommentResponse("Otro", 0)));
        var snapshot = List.copyOf(comments);
        var response = mapper.toResponse(json.createObjectNode(), comments);
        assertThat(comments).containsExactlyElementsOf(snapshot);
        assertThat(response.get("comments")).isEqualTo(json.valueToTree(snapshot));
        assertThat(response.get("comments").size()).isEqualTo(3);
    }

    @Test
    void rejectsNullShow() {
        assertThatIllegalArgumentException().isThrownBy(() -> mapper.toResponse(null, List.of()));
    }

    @Test
    void rejectsNullComments() {
        assertThatIllegalArgumentException().isThrownBy(() -> mapper.toResponse(json.createObjectNode(), null));
    }

    @Test
    void rejectsNullElementsWithoutMutatingTheOriginal() {
        ObjectNode original = json.createObjectNode().put("comments", "provider");
        ObjectNode snapshot = original.deepCopy();
        assertThatIllegalArgumentException().isThrownBy(() -> mapper.toResponse(original,
                Arrays.asList(new CommentResponse("Valid", 5), null)));
        assertThat(original).isEqualTo(snapshot);
    }
}
