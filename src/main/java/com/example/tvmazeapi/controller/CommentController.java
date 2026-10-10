package com.example.tvmazeapi.controller;

import com.example.tvmazeapi.dto.request.CreateCommentRequest;
import com.example.tvmazeapi.dto.response.CommentCreatedResponse;
import com.example.tvmazeapi.service.CommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shows")
public class CommentController {
    private final CommentService service;

    public CommentController(CommentService service) {
        this.service = service;
    }

    @PostMapping("/{show_id}/comments")
    public ResponseEntity<CommentCreatedResponse> create(
            @PathVariable(name = "show_id") @Positive Integer showId,
            @RequestBody @Valid CreateCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(showId, request));
    }
}
