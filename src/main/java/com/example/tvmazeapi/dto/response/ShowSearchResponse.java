package com.example.tvmazeapi.dto.response;

import java.util.List;

public record ShowSearchResponse(
        Integer id,
        String name,
        String channel,
        String summary,
        List<String> genres
) {
}
