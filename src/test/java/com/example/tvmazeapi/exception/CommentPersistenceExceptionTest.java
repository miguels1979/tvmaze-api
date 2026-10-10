package com.example.tvmazeapi.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommentPersistenceExceptionTest {
    @Test
    void preservesMessageWithoutCause() {
        var exception = new CommentPersistenceException("Persistence failed");
        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception).hasMessage("Persistence failed").hasNoCause();
    }

    @Test
    void preservesOriginalCause() {
        var cause = new IllegalStateException("Internal diagnostic");
        var exception = new CommentPersistenceException("Persistence failed", cause);
        assertThat(exception).hasMessage("Persistence failed").hasCause(cause);
    }
}
