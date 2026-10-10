package com.example.tvmazeapi.repository;

import com.example.tvmazeapi.document.CommentDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CommentRepository extends MongoRepository<CommentDocument, String> {
}
