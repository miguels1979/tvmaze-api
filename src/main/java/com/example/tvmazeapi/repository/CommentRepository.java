package com.example.tvmazeapi.repository;

import com.example.tvmazeapi.document.CommentDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Collection;
import java.util.List;

public interface CommentRepository extends MongoRepository<CommentDocument, String> {
    List<CommentDocument> findByShowIdIn(Collection<Integer> showIds);
}
