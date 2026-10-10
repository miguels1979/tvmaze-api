package com.example.tvmazeapi.exception;

public class CommentPersistenceException extends RuntimeException {
    public CommentPersistenceException(String message) {
        super(message);
    }

    public CommentPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
