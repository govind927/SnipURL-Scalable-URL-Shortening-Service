package com.urlshortener.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Uniform error response shape for all API errors.
 * Consistent error format is a sign of a well-designed API.
 */
@Data
@Builder
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private String path;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
