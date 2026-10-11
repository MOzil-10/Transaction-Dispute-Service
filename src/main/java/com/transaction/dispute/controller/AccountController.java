package com.transaction.dispute.controller;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.model.dto.AccountResponseDto;
import com.transaction.dispute.model.dto.ErrorResponseDto;
import com.transaction.dispute.security.JwtService;
import com.transaction.dispute.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Account", description = "Read-only overview of the authenticated customer and their primary account")
public class AccountController {

    private final AccountService accountService;
    private final JwtService jwtService;

    @Operation(summary = "Get account overview", description = "Returns the authenticated customer's profile and primary account details, including the live balance. Read-only.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account overview retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AccountResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Bearer JWT token missing or invalid", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "No account found for authenticated customer", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping
    public ResponseEntity<AccountResponseDto> getAccountOverview(@RequestHeader(AppConstants.AUTHORIZATION_HEADER) String authorizationHeader) {
        log.info("Received request to view account overview");

        String token = authorizationHeader.substring(AppConstants.BEARER_PREFIX.length());
        Long customerId = jwtService.extractCustomerId(token);

        AccountResponseDto result = accountService.getAccountOverview(customerId);
        log.info("Returning account overview — customerId={}", customerId);

        return ResponseEntity.ok(result);
    }
}