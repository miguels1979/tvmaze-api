package com.example.tvmazeapi.service;

import com.example.tvmazeapi.client.TvMazeClient;
import com.example.tvmazeapi.dto.response.ShowSearchResponse;
import com.example.tvmazeapi.mapper.ShowSearchMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShowService {

    private final TvMazeClient tvMazeClient;
    private final ShowSearchMapper showSearchMapper;

    public ShowService(TvMazeClient tvMazeClient, ShowSearchMapper showSearchMapper) {
        this.tvMazeClient = tvMazeClient;
        this.showSearchMapper = showSearchMapper;
    }

    public List<ShowSearchResponse> search(String searchQuery) {
        if (searchQuery == null || searchQuery.isBlank()) {
            throw new IllegalArgumentException("Search query must not be blank");
        }

        String query = searchQuery.strip();
        return tvMazeClient.searchShows(query).stream()
                .map(result -> showSearchMapper.toResponse(result.show()))
                .toList();
    }

    public ObjectNode getShow(Integer showId) {
        if (showId == null || showId <= 0) {
            throw new IllegalArgumentException("Show ID must be a positive integer");
        }

        return tvMazeClient.getShow(showId);
    }
}
