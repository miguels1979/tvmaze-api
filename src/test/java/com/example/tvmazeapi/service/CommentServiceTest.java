package com.example.tvmazeapi.service;

import com.example.tvmazeapi.document.CommentDocument;
import com.example.tvmazeapi.dto.request.CreateCommentRequest;
import com.example.tvmazeapi.dto.response.CommentCreatedResponse;
import com.example.tvmazeapi.exception.CommentPersistenceException;
import com.example.tvmazeapi.repository.CommentRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    @Mock
    private CommentRepository repository;
    private CommentService service;

    @BeforeEach
    void setUp() {
        service = new CommentService(repository, FACTORY.getValidator());
    }

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, 5})
    void insertsExactDataWithUuidAndReturnsCreated(int rating) {
        when(repository.insert(any(CommentDocument.class))).thenAnswer(call -> call.getArgument(0));
        String text = "  <p>Niña 日本語 😀</p>\n  ";
        var response = service.create(1, new CreateCommentRequest(text, rating));

        var captor = ArgumentCaptor.forClass(CommentDocument.class);
        verify(repository).insert(captor.capture());
        var document = captor.getValue();
        assertThat(UUID.fromString(document.id()).toString()).isEqualTo(document.id());
        assertThat(document.id()).isNotEqualTo("1");
        assertThat(document.showId()).isEqualTo(1);
        assertThat(document.comment()).isEqualTo(text);
        assertThat(document.rating()).isEqualTo(rating);
        assertThat(response).isEqualTo(new CommentCreatedResponse(document.id(), 1, "created"));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void repeatedRequestsCreateIndependentIds() {
        when(repository.insert(any(CommentDocument.class))).thenAnswer(call -> call.getArgument(0));
        var request = new CreateCommentRequest("Good", 3);
        var first = service.create(1, request);
        var second = service.create(1, request);
        assertThat(first.id()).isNotEqualTo(second.id());
        var captor = ArgumentCaptor.forClass(CommentDocument.class);
        verify(repository, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(CommentDocument::showId).containsExactly(1, 1);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void buildsResponseFromConfirmedInsertResult() {
        var inserted = new CommentDocument("confirmed-id", 42, "Stored", 5);
        when(repository.insert(any(CommentDocument.class))).thenReturn(inserted);
        assertThat(service.create(1, new CreateCommentRequest("Original", 3)))
                .isEqualTo(new CommentCreatedResponse("confirmed-id", 42, "created"));
        verify(repository).insert(any(CommentDocument.class));
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void wrapsPersistenceFailuresIncludingUuidCollisionWithoutRetry(boolean duplicate) {
        var cause = duplicate ? new DuplicateKeyException("Internal duplicate")
                : new DataAccessResourceFailureException("Internal connection details");
        when(repository.insert(any(CommentDocument.class))).thenThrow(cause);
        assertThatThrownBy(() -> service.create(1, new CreateCommentRequest("Good", 3)))
                .isInstanceOf(CommentPersistenceException.class)
                .hasMessage("Unable to persist the comment").hasCause(cause);
        verify(repository).insert(any(CommentDocument.class));
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void rejectsInvalidShowIdBeforePersistence(Integer showId) {
        assertThatThrownBy(() -> service.create(showId, new CreateCommentRequest("Good", 3)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsNullRequestBeforePersistence() {
        assertThatThrownBy(() -> service.create(1, null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void validatesDirectJavaCallsBeforePersistence(CreateCommentRequest request) {
        assertThatThrownBy(() -> service.create(1, request))
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(repository);
    }

    static Stream<CreateCommentRequest> invalidRequests() {
        return Stream.of(new CreateCommentRequest(null, 3), new CreateCommentRequest("", 3),
                new CreateCommentRequest(" \t\n", 3), new CreateCommentRequest("a".repeat(2001), 3),
                new CreateCommentRequest("Good", null), new CreateCommentRequest("Good", -1),
                new CreateCommentRequest("Good", 6));
    }

    @Test
    void doesNotWrapUnexpectedProgrammingErrors() {
        var cause = new IllegalStateException("Unexpected error");
        when(repository.insert(any(CommentDocument.class))).thenThrow(cause);
        assertThatThrownBy(() -> service.create(1, new CreateCommentRequest("Good", 3)))
                .isSameAs(cause);
        verify(repository).insert(any(CommentDocument.class));
        verifyNoMoreInteractions(repository);
    }
}
