package com.transaction.dispute.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "User login credentials payload")
public record LoginRequestDto(

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Schema(description = "Registered customer email", example = "rita.sithole@example.co.za")
        String email,

        @NotBlank(message = "Password is required")
        @Schema(description = "Customer account password", example = "P@ssword2026!")
        String password
) {}