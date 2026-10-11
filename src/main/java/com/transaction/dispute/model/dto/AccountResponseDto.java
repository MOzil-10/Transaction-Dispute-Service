package com.transaction.dispute.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "AccountResponse", description = "Read-only overview of the authenticated customer and their primary account")
public record AccountResponseDto(

        @Schema(description = "Customer identifier", example = "1000001")
        Long customerId,

        @Schema(description = "Customer first name", example = "Trevor")
        String firstName,

        @Schema(description = "Customer last name", example = "Sesoko")
        String lastName,

        @Schema(description = "Customer email address", example = "trevor.sesoko@example.co.za")
        String email,

        @Schema(description = "Account number", example = "5000001")
        String accountNumber,

        @Schema(description = "Account type", example = "MAIN_ACCOUNT")
        String accountType,

        @Schema(description = "Current available ledger balance", example = "12450.00")
        BigDecimal balance,

        @Schema(description = "ISO 4217 currency code", example = "ZAR")
        String currency
) {}