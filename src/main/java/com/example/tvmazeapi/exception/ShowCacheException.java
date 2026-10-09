package com.example.tvmazeapi.exception;

public class ShowCacheException extends RuntimeException {

    public enum Reason {
        UNAVAILABLE,
        INVALID_DATA
    }

    private final Reason reason;

    public ShowCacheException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ShowCacheException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
