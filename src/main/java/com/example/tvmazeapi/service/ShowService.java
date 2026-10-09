package com.example.tvmazeapi.service;

import com.example.tvmazeapi.client.TvMazeClient;
import com.example.tvmazeapi.document.CachedShowDocument;
import com.example.tvmazeapi.exception.ShowCacheException;
import com.example.tvmazeapi.mapper.ShowCacheMapper;
import com.example.tvmazeapi.repository.CachedShowRepository;
import com.example.tvmazeapi.dto.response.ShowSearchResponse;
import com.example.tvmazeapi.mapper.ShowSearchMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;

@Service
public class ShowService {

    private final TvMazeClient tvMazeClient;
    private final ShowSearchMapper showSearchMapper;
    private final CachedShowRepository cachedShowRepository;
    private final ShowCacheMapper showCacheMapper;

    public ShowService(TvMazeClient tvMazeClient, ShowSearchMapper showSearchMapper,
                       CachedShowRepository cachedShowRepository, ShowCacheMapper showCacheMapper) {
        this.tvMazeClient = tvMazeClient;
        this.showSearchMapper = showSearchMapper;
        this.cachedShowRepository = cachedShowRepository;
        this.showCacheMapper = showCacheMapper;
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

        Optional<CachedShowDocument> cachedShow = findCachedShow(showId);
        if (cachedShow.isPresent()) {
            return showCacheMapper.toShow(showId, cachedShow.get());
        }

        ObjectNode show = tvMazeClient.getShow(showId);
        return storeOrReadWinner(showId, show);
    }

    private Optional<CachedShowDocument> findCachedShow(Integer showId) {
        try {
            return cachedShowRepository.findById(showId);
        } catch (DataAccessException exception) {
            throw new ShowCacheException(ShowCacheException.Reason.UNAVAILABLE,
                    "Unable to read the show cache", exception);
        }
    }

    private ObjectNode storeOrReadWinner(Integer showId, ObjectNode show) {
        CachedShowDocument document = showCacheMapper.toDocument(showId, show);
        try {
            cachedShowRepository.insert(document);
        } catch (DuplicateKeyException exception) {
            CachedShowDocument winner = findCachedShow(showId).orElseThrow(() ->
                    new ShowCacheException(ShowCacheException.Reason.UNAVAILABLE,
                            "Concurrent cache entry could not be recovered", exception));
            return showCacheMapper.toShow(showId, winner);
        } catch (DataAccessException exception) {
            throw new ShowCacheException(ShowCacheException.Reason.UNAVAILABLE,
                    "Unable to persist the show cache", exception);
        }
        return show;
    }
}
