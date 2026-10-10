package com.example.tvmazeapi.dto.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateCommentRequestTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterAll
    static void closeValidatorFactory() {
        FACTORY.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 5})
    void acceptsValidCommentAndIntegerRating(int rating) throws Exception {
        var request = objectMapper.readValue("{\"comment\":\"Good show\",\"rating\":" + rating + "}",
                CreateCommentRequest.class);
        assertThat(request.comment()).isEqualTo("Good show");
        assertThat(request.rating()).isEqualTo(rating);
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t\n"})
    void rejectsNullEmptyOrBlankComment(String comment) throws Exception {
        var tree = objectMapper.createObjectNode().put("comment", comment).put("rating", 3);
        var request = objectMapper.treeToValue(tree, CreateCommentRequest.class);
        assertInvalidProperty(request, "comment");
    }

    @Test
    void rejectsMissingComment() throws Exception {
        var request = objectMapper.readValue("{\"rating\":3}", CreateCommentRequest.class);
        assertThat(request.comment()).isNull();
        assertInvalidProperty(request, "comment");
    }

    @Test
    void acceptsExactly2000Utf16Units() throws Exception {
        String comment = "😀".repeat(1000);
        assertThat(comment.length()).isEqualTo(2000);
        var request = readComment(comment);
        assertThat(request.comment()).isEqualTo(comment);
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void rejects2001Utf16Units() throws Exception {
        String comment = "😀".repeat(1000) + "a";
        assertThat(comment.length()).isEqualTo(2001);
        assertInvalidProperty(readComment(comment), "comment");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 6})
    void rejectsIntegerRatingsOutsideIntervalDuringValidation(int rating) throws Exception {
        var request = objectMapper.readValue("{\"comment\":\"Good\",\"rating\":" + rating + "}",
                CreateCommentRequest.class);
        assertThat(request.rating()).isEqualTo(rating);
        assertInvalidProperty(request, "rating");
    }

    @Test
    void rejectsMissingRatingDuringValidation() throws Exception {
        var request = objectMapper.readValue("{\"comment\":\"Good\"}", CreateCommentRequest.class);
        assertThat(request.rating()).isNull();
        assertInvalidProperty(request, "rating");
    }

    @Test
    void rejectsNullRatingDuringValidation() throws Exception {
        var request = objectMapper.readValue("{\"comment\":\"Good\",\"rating\":null}",
                CreateCommentRequest.class);
        assertThat(request.rating()).isNull();
        assertInvalidProperty(request, "rating");
    }

    @ParameterizedTest
    @ValueSource(strings = {"4.0", "4.5", "4e0", "\"5\"", "\"\"", "true", "false", "[]", "[5]", "{}",
            "{\"value\":5}", "2147483648", "-2147483649", "999999999999999999999999999999"})
    void rejectsNonIntegerTokensAndIntegerOverflowDuringDeserialization(String ratingJson) {
        String json = "{\"comment\":\"Good\",\"rating\":" + ratingJson + "}";
        assertThatThrownBy(() -> objectMapper.readValue(json, CreateCommentRequest.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void preservesExactOriginalText() throws Exception {
        String comment = "  <p>Niña 日本語 😀</p>\n\t final  ";
        var request = readComment(comment);
        assertThat(request.comment()).isEqualTo(comment);
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void doesNotChangeGlobalJacksonIntegerCoercion() throws Exception {
        assertThat(objectMapper.readValue("\"5\"", Integer.class)).isEqualTo(5);
        assertThatThrownBy(() -> objectMapper.readValue("{\"comment\":\"Good\",\"rating\":\"5\"}",
                CreateCommentRequest.class)).isInstanceOf(JsonProcessingException.class);
        assertThat(objectMapper.readValue("\"5\"", Integer.class)).isEqualTo(5);
    }

    private CreateCommentRequest readComment(String comment) throws Exception {
        var tree = objectMapper.createObjectNode().put("comment", comment).put("rating", 3);
        return objectMapper.treeToValue(tree, CreateCommentRequest.class);
    }

    private void assertInvalidProperty(CreateCommentRequest request, String property) {
        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly(property);
    }
}
