package com.transaction.dispute.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "RegisterRequest", description = "Customer self-registration payload")
public record RegisterRequestDto(

        @NotBlank(message = "First name is required")
        @Schema(description = "Customer first name", example = "Mesut")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Schema(description = "Customer last name", example = "Ozil")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Schema(description = "Unique customer email address", example = "mesut.ozil@example.co.za")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters long")
        @Schema(description = "Raw password to be hashed", example = "P@ssword2026!")
        String password
) {}