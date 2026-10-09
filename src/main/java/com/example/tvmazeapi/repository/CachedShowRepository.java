package com.example.tvmazeapi.repository;

import com.example.tvmazeapi.document.CachedShowDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CachedShowRepository extends MongoRepository<CachedShowDocument, Integer> {
}
