package dev.fire.api.web;

import java.time.Instant;

import dev.fire.api.service.InvalidProjectStateException;
import dev.fire.api.service.ProjectNotFoundException;
import dev.fire.api.service.UnknownProviderException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ProjectNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ProjectNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidProjectStateException.class)
    ResponseEntity<ApiError> handleState(InvalidProjectStateException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "PROJECT_NOT_STARTABLE", exception.getMessage(), request);
    }

    @ExceptionHandler(UnknownProviderException.class)
    ResponseEntity<ApiError> handleProvider(UnknownProviderException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "UNKNOWN_PROVIDER", exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, request);
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI()));
    }
}
