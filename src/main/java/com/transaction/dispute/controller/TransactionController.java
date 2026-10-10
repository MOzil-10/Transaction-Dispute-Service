package com.transaction.dispute.controller;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.model.dto.ErrorResponseDto;
import com.transaction.dispute.model.dto.TransactionResponseDto;
import com.transaction.dispute.security.JwtService;
import com.transaction.dispute.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Transaction Management", description = "Endpoints for retrieving paginated customer transaction history for dispute selection")
public class TransactionController {

    private final TransactionService transactionService;
    private final JwtService jwtService;

    @Operation(
            summary = "Get customer transactions",
            description = "Returns a paginated list of transactions linked to the authenticated customer account, sorted by transaction date descending.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transactions retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Bearer JWT token missing or invalid",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "No account found for authenticated customer",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping
    public ResponseEntity<Page<TransactionResponseDto>> getTransactions(
            @RequestHeader(AppConstants.AUTHORIZATION_HEADER) String authorizationHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        log.info("Received request to view transactions");

        String token = authorizationHeader.substring(AppConstants.BEARER_PREFIX.length());
        Long customerId = jwtService.extractCustomerId(token);

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate"));
        Page<TransactionResponseDto> result = transactionService.getTransactionPage(customerId, pageable);

        log.info("Returning transaction page — customerId={}, page={}, totalElements={}",
                customerId, page, result.getTotalElements());
        return ResponseEntity.ok(result);
    }
}

