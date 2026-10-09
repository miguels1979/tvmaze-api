package com.example.tvmazeapi.exception;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ShowCacheException.class)
    public ProblemDetail handleShowCacheException(ShowCacheException exception) {
        HttpStatus status = switch (exception.getReason()) {
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INVALID_DATA -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        String detail = switch (exception.getReason()) {
            case UNAVAILABLE -> "El almacenamiento de series no está disponible temporalmente.";
            case INVALID_DATA -> "No fue posible recuperar correctamente la serie almacenada.";
        };

        return ProblemDetail.forStatusAndDetail(status, detail);
    }

    @ExceptionHandler(ShowNotFoundException.class)
    public ProblemDetail handleShowNotFoundException(ShowNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "No se encontró la serie solicitada."
        );
    }

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
                "El parámetro " + exception.getParameterName() + " es obligatorio."
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
                validationDetail(exception)
        );
        return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String detail = "Un parámetro de la solicitud tiene un tipo no válido.";
        if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            detail = "El parámetro " + mismatch.getName() + " tiene un tipo no válido.";
            if (Integer.class.equals(mismatch.getRequiredType())) {
                detail = "El parámetro " + mismatch.getName() + " debe ser un entero válido.";
            }
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    private String validationDetail(HandlerMethodValidationException exception) {
        for (var result : exception.getParameterValidationResults()) {
            MethodParameter parameter = result.getMethodParameter();
            String name = requestParameterName(parameter);
            if (parameter.hasParameterAnnotation(NotBlank.class)) {
                return "El parámetro " + name + " no debe estar vacío ni contener únicamente espacios.";
            }
            if (parameter.hasParameterAnnotation(Positive.class)) {
                return "El parámetro " + name + " debe ser mayor que cero.";
            }
        }
        return "Uno o más parámetros de la solicitud no cumplen las restricciones requeridas.";
    }

    private String requestParameterName(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            if (!requestParam.name().isBlank()) {
                return requestParam.name();
            }
            if (!requestParam.value().isBlank()) {
                return requestParam.value();
            }
        }

        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null) {
            if (!pathVariable.name().isBlank()) {
                return pathVariable.name();
            }
            if (!pathVariable.value().isBlank()) {
                return pathVariable.value();
            }
        }

        return "de la solicitud";
    }
}
