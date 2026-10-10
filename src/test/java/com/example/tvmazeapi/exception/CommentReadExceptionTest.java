package com.example.tvmazeapi.exception;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class CommentReadExceptionTest {
    @ParameterizedTest
    @EnumSource(CommentReadException.Reason.class)
    void preservesReasonAndMessage(CommentReadException.Reason reason) {
        var exception = new CommentReadException(reason, "Read failed");
        assertThat(exception).isInstanceOf(RuntimeException.class).hasMessage("Read failed").hasNoCause();
        assertThat(exception.getReason()).isEqualTo(reason);
    }

    @ParameterizedTest
    @EnumSource(CommentReadException.Reason.class)
    void preservesTechnicalCause(CommentReadException.Reason reason) {
        var cause = new IllegalStateException("Internal diagnostic");
        var exception = new CommentReadException(reason, "Read failed", cause);
        assertThat(exception).hasMessage("Read failed").hasCause(cause);
        assertThat(exception.getReason()).isEqualTo(reason);
    }
}
