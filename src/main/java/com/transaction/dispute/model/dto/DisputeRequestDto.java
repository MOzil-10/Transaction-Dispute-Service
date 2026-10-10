package com.transaction.dispute.model.dto;

import com.transaction.dispute.model.enums.DisputeReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "DisputeRequest", description = "Request payload submitted by customer to initiate a transaction dispute")
public record DisputeRequestDto(

        @NotNull(message = "Transaction ID is required")
        @Schema(description = "Unique numeric sequence identifier of the transaction being disputed", example = "5000102")
        Long transactionId,

        @NotNull(message = "Dispute reason is required")
        @Schema(description = "Categorized business reason for lodging the dispute", example = "UNAUTHORISED_MANDATE")
        DisputeReason reason,

        @Schema(description = "Optional customer notes explaining why the transaction is being disputed", example = "Unauthorised debit order processed after contract cancellation notice was confirmed.")
        String description
) {}