package com.example.tvmazeapi.service;

import com.example.tvmazeapi.document.CommentDocument;
import com.example.tvmazeapi.dto.response.CommentResponse;
import com.example.tvmazeapi.exception.CommentReadException;
import com.example.tvmazeapi.repository.CommentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentQueryServiceTest {
    @Mock private CommentRepository repository;
    @InjectMocks private CommentQueryService service;

    @Test
    void emptyInputDoesNotQuery() {
        assertThat(service.findByShowIds(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void queriesUniqueIdsOnceInFirstAppearanceOrderAndGroupsSortedComments() {
        String text = "  <p>Niña 日本語 😀</p>\n  ";
        var documents = new ArrayList<>(List.of(
                new CommentDocument("00000000-0000-4000-8000-000000000003", 2, "Later", 5),
                new CommentDocument("00000000-0000-4000-8000-000000000002", 1, text, 0),
                new CommentDocument("00000000-0000-4000-8000-000000000001", 2, "Earlier", 3),
                new CommentDocument("00000000-0000-4000-8000-000000000004", 2, "Earlier", 3)));
        var snapshot = List.copyOf(documents);
        when(repository.findByShowIdIn(List.of(2, 1, 3))).thenReturn(documents);
        var result = service.findByShowIds(List.of(2, 1, 2, 3));
        assertThat(result).containsOnlyKeys(1, 2);
        assertThat(result.get(1)).containsExactly(new CommentResponse(text, 0));
        assertThat(result.get(2)).containsExactly(new CommentResponse("Earlier", 3),
                new CommentResponse("Later", 5), new CommentResponse("Earlier", 3));
        assertThat(result).doesNotContainKey(3);
        assertThat(documents).containsExactlyElementsOf(snapshot);
        assertThatThrownBy(() -> result.put(3, List.of())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.get(2).clear()).isInstanceOf(UnsupportedOperationException.class);
        verify(repository).findByShowIdIn(List.of(2, 1, 3));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void noStoredCommentsReturnsEmptyImmutableMap() {
        when(repository.findByShowIdIn(List.of(1))).thenReturn(List.of());
        var result = service.findByShowIds(List.of(1));
        assertThat(result).isEmpty();
        assertThatThrownBy(() -> result.put(1, List.of())).isInstanceOf(UnsupportedOperationException.class);
        verify(repository).findByShowIdIn(List.of(1));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void acceptsExactly2000Utf16Units() {
        String text = "😀".repeat(1000);
        when(repository.findByShowIdIn(List.of(1))).thenReturn(List.of(new CommentDocument("id", 1, text, 5)));
        assertThat(service.findByShowIds(List.of(1)).get(1)).containsExactly(new CommentResponse(text, 5));
        verify(repository).findByShowIdIn(List.of(1));
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void rejectsInvalidInputWithoutQuery(Collection<Integer> ids) {
        assertThatThrownBy(() -> service.findByShowIds(ids)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    static Stream<Collection<Integer>> invalidInputs() {
        return Stream.of(null, Arrays.asList(1, null), List.of(0), List.of(-1), List.of(1, -2));
    }

    @ParameterizedTest
    @MethodSource("invalidDocuments")
    void rejectsCorruptDataWithoutSilentlyOmittingIt(CommentDocument document) {
        when(repository.findByShowIdIn(List.of(1))).thenReturn(Arrays.asList(
                new CommentDocument("valid", 1, "Good", 3), document));
        assertThatThrownBy(() -> service.findByShowIds(List.of(1)))
                .isInstanceOfSatisfying(CommentReadException.class, error ->
                        assertThat(error.getReason()).isEqualTo(CommentReadException.Reason.INVALID_DATA));
        verify(repository).findByShowIdIn(List.of(1));
        verifyNoMoreInteractions(repository);
    }

    static Stream<CommentDocument> invalidDocuments() {
        return Stream.of(null,
                new CommentDocument(null, 1, "Good", 3), new CommentDocument("", 1, "Good", 3),
                new CommentDocument(" ", 1, "Good", 3), new CommentDocument("id", null, "Good", 3),
                new CommentDocument("id", 0, "Good", 3), new CommentDocument("id", -1, "Good", 3),
                new CommentDocument("id", 2, "Good", 3), new CommentDocument("id", 1, null, 3),
                new CommentDocument("id", 1, "", 3), new CommentDocument("id", 1, " \t\n", 3),
                new CommentDocument("id", 1, "a".repeat(2001), 3),
                new CommentDocument("id", 1, "Good", null), new CommentDocument("id", 1, "Good", -1),
                new CommentDocument("id", 1, "Good", 6));
    }

    @Test
    void rejectsNullRepositoryResult() {
        when(repository.findByShowIdIn(List.of(1))).thenReturn(null);
        assertThatThrownBy(() -> service.findByShowIds(List.of(1)))
                .isInstanceOfSatisfying(CommentReadException.class, error ->
                        assertThat(error.getReason()).isEqualTo(CommentReadException.Reason.INVALID_DATA));
        verify(repository).findByShowIdIn(List.of(1));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void wrapsDataAccessFailureAndPreservesCause() {
        var cause = new DataAccessResourceFailureException("Internal diagnostic");
        when(repository.findByShowIdIn(List.of(1))).thenThrow(cause);
        assertThatThrownBy(() -> service.findByShowIds(List.of(1)))
                .isInstanceOfSatisfying(CommentReadException.class, error ->
                        assertThat(error.getReason()).isEqualTo(CommentReadException.Reason.UNAVAILABLE))
                .hasCause(cause).hasMessage("Unable to read comments");
        verify(repository).findByShowIdIn(List.of(1));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void doesNotWrapUnexpectedErrors() {
        var cause = new IllegalStateException("Programming error");
        when(repository.findByShowIdIn(List.of(1))).thenThrow(cause);
        assertThatThrownBy(() -> service.findByShowIds(List.of(1))).isSameAs(cause);
        verify(repository).findByShowIdIn(List.of(1));
        verifyNoMoreInteractions(repository);
    }
}
