package com.example.tvmazeapi.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommentCreatedResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesExactlyApprovedFieldsWithSnakeCaseShowId() throws Exception {
        var response = new CommentCreatedResponse("75b99a79-8794-4817-9f8e-f59a62446b63", 1, "created");

        var json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json).isEqualTo(objectMapper.readTree("""
                {"id":"75b99a79-8794-4817-9f8e-f59a62446b63","show_id":1,"status":"created"}
                """));
        assertThat(json.size()).isEqualTo(3);
        assertThat(json.has("showId")).isFalse();
    }
}
