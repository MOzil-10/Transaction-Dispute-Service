package com.transaction.dispute.controller;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.model.dto.DisputeRequestDto;
import com.transaction.dispute.model.dto.DisputeResponseDto;
import com.transaction.dispute.model.dto.ErrorResponseDto;
import com.transaction.dispute.security.JwtService;
import com.transaction.dispute.service.DisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/disputes")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dispute Management", description = "Endpoints for submitting transaction disputes and retrieving dispute history")
public class DisputeController {

    private final DisputeService disputeService;
    private final JwtService jwtService;

    @Operation(summary = "Submit a transaction dispute", description = "Validates transaction ownership, calculates a risk score, applies the decision matrix, persists the dispute, and returns the routing outcome.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Dispute submitted and decision evaluated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DisputeResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Duplicate dispute or validation error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing JWT", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Transaction does not belong to authenticated customer", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Transaction or customer not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping
    public ResponseEntity<DisputeResponseDto> submitDispute(@RequestHeader(AppConstants.AUTHORIZATION_HEADER) String authorizationHeader, @Valid @RequestBody DisputeRequestDto request
    ) {
        log.info("Received dispute submission request — transactionId={}", request.transactionId());

        String token = authorizationHeader.substring(AppConstants.BEARER_PREFIX.length());
        Long customerId = jwtService.extractCustomerId(token);

        DisputeResponseDto response = disputeService.submitDispute(customerId, request);
        log.info("Dispute submission complete — disputeReference={}, status={}", response.disputeReference(), response.status());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get dispute history", description = "Returns a paginated list of all disputes submitted by the authenticated customer, sorted by submission date descending.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dispute history retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing JWT", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Customer not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping
    public ResponseEntity<Page<DisputeResponseDto>> getDisputeHistory(@RequestHeader(AppConstants.AUTHORIZATION_HEADER) String authorizationHeader, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size
    ) {
        log.info("Received request to view dispute history");

        String token = authorizationHeader.substring(AppConstants.BEARER_PREFIX.length());
        Long customerId = jwtService.extractCustomerId(token);

        Page<DisputeResponseDto> result = disputeService.getDisputeHistory(customerId, PageRequest.of(page, size));
        log.info("Returning dispute history — customerId={}, totalElements={}", customerId, result.getTotalElements());
        return ResponseEntity.ok(result);
    }
}

