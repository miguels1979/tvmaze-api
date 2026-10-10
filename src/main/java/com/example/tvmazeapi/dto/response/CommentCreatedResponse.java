package com.example.tvmazeapi.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CommentCreatedResponse(String id, @JsonProperty("show_id") Integer showId, String status) {
}
