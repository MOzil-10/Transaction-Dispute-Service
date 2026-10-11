package com.transaction.dispute.model.dto;

import com.transaction.dispute.model.enums.MandateType;
import com.transaction.dispute.model.enums.TransactionCategory;
import com.transaction.dispute.model.enums.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(name = "TransactionResponse", description = "Individual transaction item details")
public record TransactionResponseDto(

        @Schema(description = "Unique numeric sequence identifier of the transaction", example = "5000102")
        Long transactionId,

        @Schema(description = "Bank-issued transaction reference", example = "TXN-90812")
        String reference,

        @Schema(description = "Name of merchant or billing entity initiating the charge", example = "FITNESS_CLUB_ZA")
        String merchant,

        @Schema(description = "Monetary value of the transaction", example = "450.00")
        BigDecimal amount,

        @Schema(description = "ISO 4217 Currency Code", example = "ZAR")
        String currency,

        @Schema(description = "Clearing framework / mandate protocol used for payment", example = "STANDARD_EFT")
        MandateType mandateType,

        @Schema(description = "Business category of the transaction (drives disputability and display)", example = "MOBILE_CONTRACT")
        TransactionCategory category,

        @Schema(description = "Current settlement status of the transaction", example = "COMPLETED")
        TransactionStatus status,

        @Schema(description = "Timestamp when the transaction cleared on the bank network", example = "2026-09-28T10:15:00+02:00")
        OffsetDateTime transactionDate,

        @Schema(description = "Whether this transaction is eligible to be disputed", example = "true")
        boolean disputable
) {}
