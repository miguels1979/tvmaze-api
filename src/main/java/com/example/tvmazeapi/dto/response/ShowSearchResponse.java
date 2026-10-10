package com.example.tvmazeapi.dto.response;

import java.util.List;

public record ShowSearchResponse(
        Integer id,
        String name,
        String channel,
        String summary,
        List<String> genres,
        List<CommentResponse> comments
) {
    public ShowSearchResponse {
        comments = comments == null ? List.of() : List.copyOf(comments);
    }

    public ShowSearchResponse withComments(List<CommentResponse> comments) {
        return new ShowSearchResponse(id, name, channel, summary, genres, comments);
    }
}
