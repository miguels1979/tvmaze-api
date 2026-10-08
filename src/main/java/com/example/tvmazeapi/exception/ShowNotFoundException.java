package com.example.tvmazeapi.exception;

public class ShowNotFoundException extends RuntimeException {

    public ShowNotFoundException(Integer showId) {
        super("TVMaze show not found: " + showId);
    }

    public ShowNotFoundException(Integer showId, Throwable cause) {
        super("TVMaze show not found: " + showId, cause);
    }
}
