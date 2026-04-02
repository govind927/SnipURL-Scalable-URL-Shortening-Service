package com.urlshortener.exception;

import com.urlshortener.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized exception handler.
 * Every error in the app returns a consistent JSON shape.
 *
 * HTTP status mapping:
 *   UrlNotFoundException           → 404 Not Found
 *   UrlExpiredException            → 410 Gone  (semantically correct — resource existed, now gone)
 *   CustomAliasAlreadyExistsException → 409 Conflict
 *   RateLimitExceededException     → 429 Too Many Requests
 *   Validation errors              → 400 Bad Request
 *   Anything else                  → 500 Internal Server Error
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(UrlNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            UrlNotFoundException ex, HttpServletRequest request) {
        log.warn("URL not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(UrlExpiredException.class)
    public ResponseEntity<ErrorResponse> handleExpired(
            UrlExpiredException ex, HttpServletRequest request) {
        log.warn("Expired URL accessed: {}", ex.getMessage());
        return build(HttpStatus.GONE, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(CustomAliasAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAliasConflict(
            CustomAliasAlreadyExistsException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(
            RateLimitExceededException ex, HttpServletRequest request) {
        log.warn("Rate limit hit: {}", ex.getMessage());
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        // Return the first validation error message
        String message = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArg(
            IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    // Catch-all — never expose stack traces to the client
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again.",
                request.getRequestURI());
    }

    // ── Helper ─────────────────────────────────────────────────────
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, String path) {
        ErrorResponse error = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .build();
        return ResponseEntity.status(status).body(error);
    }
}
