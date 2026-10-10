package com.example.tvmazeapi.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentResponseTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesExactlyCommentAndRating() throws Exception {
        var response = new CommentResponse("Excelente", 5);
        assertThat(mapper.<JsonNode>valueToTree(response)).isEqualTo(mapper.readTree(
                "{\"comment\":\"Excelente\",\"rating\":5}"));
    }

    @Test
    void serializesSixFieldsWithNestedCommentsAndPreservedOriginalValues() throws Exception {
        var response = new ShowSearchResponse(1, "Show", null, "<p>HTML 日本語</p>",
                List.of("Drama", "Comedy"), List.of(new CommentResponse("  Niña 😀  ", 5)));
        assertThat(mapper.<JsonNode>valueToTree(response)).isEqualTo(mapper.readTree("""
                {"id":1,"name":"Show","channel":null,"summary":"<p>HTML 日本語</p>",
                 "genres":["Drama","Comedy"],"comments":[{"comment":"  Niña 😀  ","rating":5}]}
                """));
    }

    @Test
    void normalizesNullCommentsToEmptyArrayWithoutChangingOtherNulls() throws Exception {
        var response = new ShowSearchResponse(1, "Show", null, null, List.of(), null);
        assertThat(response.comments()).isEmpty();
        assertThat(mapper.<JsonNode>valueToTree(response)).isEqualTo(mapper.readTree("""
                {"id":1,"name":"Show","channel":null,"summary":null,"genres":[],"comments":[]}
                """));
    }

    @Test
    void withCommentsCopiesAllFiveFieldsAndLeavesOriginalUnchanged() {
        var original = new ShowSearchResponse(1, "Show", "Network", "<p>HTML</p>",
                List.of("Drama", "Comedy"), List.of());
        var comments = List.of(new CommentResponse("Good", 5));
        var enriched = original.withComments(comments);
        assertThat(enriched).isNotSameAs(original);
        assertThat(enriched.id()).isEqualTo(original.id());
        assertThat(enriched.name()).isEqualTo(original.name());
        assertThat(enriched.channel()).isEqualTo(original.channel());
        assertThat(enriched.summary()).isEqualTo(original.summary());
        assertThat(enriched.genres()).isSameAs(original.genres());
        assertThat(enriched.comments()).containsExactlyElementsOf(comments);
        assertThat(original.comments()).isEmpty();
    }

    @Test
    void copiesInputListsAndPreventsModification() {
        var input = new ArrayList<>(List.of(new CommentResponse("First", 0)));
        var original = new ShowSearchResponse(1, "Show", null, null, List.of(), input);
        var enriched = original.withComments(input);
        input.add(new CommentResponse("Second", 5));
        assertThat(original.comments()).hasSize(1);
        assertThat(enriched.comments()).hasSize(1);
        assertThatThrownBy(() -> original.comments().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> enriched.comments().add(new CommentResponse("Other", 3)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withNullCommentsReturnsEmptyListWithoutMutatingOriginal() {
        var original = new ShowSearchResponse(1, "Show", null, null, List.of(),
                List.of(new CommentResponse("Good", 3)));
        assertThat(original.withComments(null).comments()).isEmpty();
        assertThat(original.comments()).containsExactly(new CommentResponse("Good", 3));
    }

    @Test
    void rejectsNullElementsInCommentsList() {
        var input = new ArrayList<CommentResponse>();
        input.add(null);
        assertThatThrownBy(() -> new ShowSearchResponse(1, "Show", null, null, List.of(), input))
                .isInstanceOf(NullPointerException.class);
    }
}
