package dev.fire.api.web;

import java.time.Instant;

import dev.fire.api.service.InvalidProjectStateException;
import dev.fire.api.service.InvalidMediaException;
import dev.fire.api.service.MediaAssetNotFoundException;
import dev.fire.api.service.MediaStorageException;
import dev.fire.api.service.InvalidRenderStateException;
import dev.fire.api.service.ProjectRenderNotFoundException;
import dev.fire.api.service.ProjectNotFoundException;
import dev.fire.api.service.UnknownProviderException;
import dev.fire.api.service.GeneratedVideoNotFoundException;
import dev.fire.api.service.PaidGenerationConfirmationRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ProjectNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ProjectNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(MediaAssetNotFoundException.class)
    ResponseEntity<ApiError> handleAssetNotFound(
            MediaAssetNotFoundException exception,
            HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "MEDIA_ASSET_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(ProjectRenderNotFoundException.class)
    ResponseEntity<ApiError> handleRenderNotFound(
            ProjectRenderNotFoundException exception,
            HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "PROJECT_RENDER_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(GeneratedVideoNotFoundException.class)
    ResponseEntity<ApiError> handleGeneratedVideoNotFound(
            GeneratedVideoNotFoundException exception,
            HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "GENERATED_VIDEO_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidRenderStateException.class)
    ResponseEntity<ApiError> handleRenderState(
            InvalidRenderStateException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "PROJECT_NOT_RENDERABLE", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidMediaException.class)
    ResponseEntity<ApiError> handleInvalidMedia(InvalidMediaException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getErrorCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(MediaStorageException.class)
    ResponseEntity<ApiError> handleMediaStorage(MediaStorageException exception, HttpServletRequest request) {
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "MEDIA_STORAGE_FAILURE",
                "Media storage is temporarily unavailable",
                request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleUploadLimit(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "MEDIA_FILE_TOO_LARGE",
                "The uploaded file exceeds the server limit",
                request);
    }

    @ExceptionHandler(InvalidProjectStateException.class)
    ResponseEntity<ApiError> handleState(InvalidProjectStateException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "PROJECT_NOT_STARTABLE", exception.getMessage(), request);
    }

    @ExceptionHandler(UnknownProviderException.class)
    ResponseEntity<ApiError> handleProvider(UnknownProviderException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "UNKNOWN_PROVIDER", exception.getMessage(), request);
    }

    @ExceptionHandler(PaidGenerationConfirmationRequiredException.class)
    ResponseEntity<ApiError> handlePaidConfirmation(
            PaidGenerationConfirmationRequiredException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "PAID_GENERATION_CONFIRMATION_REQUIRED", exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> handleUnreadableRequest(Exception exception, HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "The request contains an unsupported or malformed value",
                request);
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
