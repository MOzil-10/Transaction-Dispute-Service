package com.transaction.dispute.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(name = "ErrorResponse", description = "Standardized error payload returned on API failures")
public record ErrorResponseDto(

        @Schema(description = "HTTP status code", example = "400")
        int status,

        @Schema(description = "Short error type description", example = "Bad Request")
        String error,

        @Schema(description = "Human-readable error message", example = "Email address is already registered")
        String message,

        @Schema(description = "API endpoint path where error occurred", example = "/api/v1/auth/register")
        String path,

        @Schema(description = "Timestamp when error occurred")
        OffsetDateTime timestamp,

        @Schema(description = "List of field-level validation error details (if applicable)")
        List<FieldErrorDto> validationErrors
) {
    public record FieldErrorDto(String field, String message) {}

    public ErrorResponseDto(int status, String error, String message, String path) {
        this(status, error, message, path, OffsetDateTime.now(), null);
    }

    public ErrorResponseDto(int status, String error, String message, String path, List<FieldErrorDto> validationErrors) {
        this(status, error, message, path, OffsetDateTime.now(), validationErrors);
    }
}