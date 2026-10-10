package com.example.tvmazeapi.service;

import com.example.tvmazeapi.document.CommentDocument;
import com.example.tvmazeapi.dto.response.CommentResponse;
import com.example.tvmazeapi.exception.CommentReadException;
import com.example.tvmazeapi.repository.CommentRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CommentQueryService {
    private final CommentRepository repository;

    public CommentQueryService(CommentRepository repository) {
        this.repository = repository;
    }

    public Map<Integer, List<CommentResponse>> findByShowIds(Collection<Integer> showIds) {
        if (showIds == null || showIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Show IDs must be positive integers");
        }
        if (showIds.isEmpty()) {
            return Map.of();
        }
        Set<Integer> uniqueIds = new LinkedHashSet<>(showIds);
        List<CommentDocument> documents;
        try {
            documents = repository.findByShowIdIn(List.copyOf(uniqueIds));
        } catch (DataAccessException exception) {
            throw new CommentReadException(CommentReadException.Reason.UNAVAILABLE,
                    "Unable to read comments", exception);
        }
        if (documents == null) {
            throw invalidData();
        }
        for (CommentDocument document : documents) {
            validateDocument(document, uniqueIds);
        }

        Map<Integer, List<CommentResponse>> grouped = new LinkedHashMap<>();
        documents.stream().sorted(Comparator.comparing(CommentDocument::id)).forEach(document ->
                grouped.computeIfAbsent(document.showId(), id -> new ArrayList<>())
                        .add(new CommentResponse(document.comment(), document.rating())));
        grouped.replaceAll((id, comments) -> List.copyOf(comments));
        return Map.copyOf(grouped);
    }

    private void validateDocument(CommentDocument document, Set<Integer> requestedIds) {
        if (document == null || document.id() == null || document.id().isBlank()
                || document.showId() == null || document.showId() <= 0
                || !requestedIds.contains(document.showId())
                || document.comment() == null || document.comment().isBlank()
                || document.comment().length() > 2000
                || document.rating() == null || document.rating() < 0 || document.rating() > 5) {
            throw invalidData();
        }
    }

    private CommentReadException invalidData() {
        return new CommentReadException(CommentReadException.Reason.INVALID_DATA,
                "Stored comments contain invalid data");
    }
}
