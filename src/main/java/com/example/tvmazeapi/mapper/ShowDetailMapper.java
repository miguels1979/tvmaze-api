package com.example.tvmazeapi.mapper;

import com.example.tvmazeapi.dto.response.CommentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShowDetailMapper {
    private final ObjectMapper objectMapper;

    public ShowDetailMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode toResponse(ObjectNode show, List<CommentResponse> comments) {
        if (show == null || comments == null) {
            throw new IllegalArgumentException("La serie y la lista de comentarios son obligatorias.");
        }

        ObjectNode response = show.deepCopy();
        ArrayNode commentsArray = objectMapper.createArrayNode();
        for (CommentResponse comment : comments) {
            if (comment == null) {
                throw new IllegalArgumentException("La lista de comentarios no admite elementos nulos.");
            }
            commentsArray.add(objectMapper.valueToTree(comment));
        }
        response.set("comments", commentsArray);
        return response;
    }
}
