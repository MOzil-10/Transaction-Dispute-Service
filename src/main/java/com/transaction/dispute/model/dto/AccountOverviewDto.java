package com.transaction.dispute.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(name = "AccountOverview", description = "Aggregated customer account summary including live balance and recent transaction history")
public record AccountOverviewDto(

        @Schema(description = "Customer checking account number", example = "1002938481")
        String accountNumber,

        @Schema(description = "Current available ledger balance", example = "12450.00")
        BigDecimal balance,

        @Schema(description = "ISO 4217 Currency Code", example = "ZAR")
        String currency,

        @Schema(description = "List of recent account transactions available for dispute selection")
        List<TransactionResponseDto> transactions
) {}