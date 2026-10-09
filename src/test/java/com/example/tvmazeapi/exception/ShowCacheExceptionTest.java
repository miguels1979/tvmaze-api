package com.example.tvmazeapi.exception;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ShowCacheExceptionTest {

    @ParameterizedTest
    @EnumSource(ShowCacheException.Reason.class)
    void preservesReasonAndMessageWithoutCause(ShowCacheException.Reason reason) {
        var error = new ShowCacheException(reason, "Controlled cache error");
        assertThat(error).isInstanceOf(RuntimeException.class).hasMessage("Controlled cache error");
        assertThat(error.getReason()).isEqualTo(reason);
        assertThat(error.getCause()).isNull();
    }

    @ParameterizedTest
    @EnumSource(ShowCacheException.Reason.class)
    void preservesReasonMessageAndCause(ShowCacheException.Reason reason) {
        var cause = new IllegalStateException("Test cause");
        var error = new ShowCacheException(reason, "Controlled cache error", cause);
        assertThat(error).hasMessage("Controlled cache error").hasCause(cause);
        assertThat(error.getReason()).isEqualTo(reason);
    }
}
