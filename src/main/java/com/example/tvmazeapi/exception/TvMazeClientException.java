package com.example.tvmazeapi.exception;

public class TvMazeClientException extends RuntimeException {

    public enum Reason {
        BAD_RESPONSE,
        RATE_LIMITED,
        TIMEOUT,
        UNAVAILABLE
    }

    private final Reason reason;

    public TvMazeClientException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public TvMazeClientException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
