package com.example.tvmazeapi.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "shows")
public record CachedShowDocument(@Id Integer id, String showJson) {
}
