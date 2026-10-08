package com.example.tvmazeapi.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(TvMazeClientException.class)
    public ProblemDetail handleTvMazeClientException(TvMazeClientException exception) {
        HttpStatus status = switch (exception.getReason()) {
            case BAD_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case RATE_LIMITED, UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
        };

        String detail = switch (exception.getReason()) {
            case BAD_RESPONSE -> "TVMaze devolvió una respuesta no válida.";
            case RATE_LIMITED -> "TVMaze alcanzó su límite de solicitudes. Intenta más tarde.";
            case UNAVAILABLE -> "TVMaze no está disponible temporalmente.";
            case TIMEOUT -> "TVMaze no respondió dentro del tiempo de espera.";
        };

        return ProblemDetail.forStatusAndDetail(status, detail);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "El parámetro search_query es obligatorio."
        );
        return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        if (exception.isForReturnValue()) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No fue posible procesar la solicitud."
            );
            return handleExceptionInternal(
                    exception, problem, headers, HttpStatus.INTERNAL_SERVER_ERROR, request
            );
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "El parámetro search_query no debe estar vacío ni contener únicamente espacios."
        );
        return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
    }
}
