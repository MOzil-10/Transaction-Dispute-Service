package com.transaction.dispute.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuthResponse", description = "Authentication token response")
public record AuthResponseDto(

        @Schema(description = "Bearer JWT access token used for authorizing API requests")
        String token,

        @Schema(description = "Token type prefix", example = "Bearer")
        String tokenType,

        @Schema(description = "Assigned customer ID", example = "5000001")
        Long customerId,

        @Schema(description = "Customer email address", example = "rita.sithole@example.co.za")
        String email
) {
    public AuthResponseDto(String token, Long customerId, String email) {
        this(token, "Bearer", customerId, email);
    }
}