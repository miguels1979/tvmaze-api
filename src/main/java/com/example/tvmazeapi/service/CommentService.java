package com.example.tvmazeapi.service;

import com.example.tvmazeapi.document.CommentDocument;
import com.example.tvmazeapi.dto.request.CreateCommentRequest;
import com.example.tvmazeapi.dto.response.CommentCreatedResponse;
import com.example.tvmazeapi.exception.CommentPersistenceException;
import com.example.tvmazeapi.repository.CommentRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CommentService {
    private final CommentRepository repository;
    private final Validator validator;

    public CommentService(CommentRepository repository, Validator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public CommentCreatedResponse create(Integer showId, CreateCommentRequest request) {
        if (showId == null || showId <= 0) {
            throw new IllegalArgumentException("Show ID must be a positive integer");
        }
        if (request == null) {
            throw new IllegalArgumentException("Comment request must not be null");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        var document = new CommentDocument(UUID.randomUUID().toString(), showId,
                request.comment(), request.rating());
        CommentDocument inserted;
        try {
            inserted = repository.insert(document);
        } catch (DataAccessException exception) {
            throw new CommentPersistenceException("Unable to persist the comment", exception);
        }
        return new CommentCreatedResponse(inserted.id(), inserted.showId(), "created");
    }
}
