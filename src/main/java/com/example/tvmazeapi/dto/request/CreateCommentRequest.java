package com.example.tvmazeapi.dto.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.IOException;

public record CreateCommentRequest(
        @NotBlank @Size(max = 2000) String comment,
        @NotNull @Min(0) @Max(5)
        @JsonDeserialize(using = CreateCommentRequest.StrictRatingDeserializer.class) Integer rating
) {
    public static class StrictRatingDeserializer extends JsonDeserializer<Integer> {

        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return context.reportInputMismatch(Integer.class, "Rating must be a JSON integer");
            }
            return parser.getIntValue();
        }
    }
}
