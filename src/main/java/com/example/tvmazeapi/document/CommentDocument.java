package com.example.tvmazeapi.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "comments")
public record CommentDocument(@Id String id, Integer showId, String comment, Integer rating) {
}
