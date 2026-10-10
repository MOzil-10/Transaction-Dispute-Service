package com.transaction.dispute.controller;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.model.dto.AuthResponseDto;
import com.transaction.dispute.model.dto.ErrorResponseDto;
import com.transaction.dispute.model.dto.LoginRequestDto;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication Management", description = "Endpoints for customer registration, account auto-provisioning, and JWT authentication")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new customer", description = "Registers a new customer account, encodes password credentials, automatically provisions a primary ZAR checking account, and returns an initial JWT authentication token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Customer registered and checking account provisioned successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation failure or email address already registered", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error occurred while processing registration", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        log.info("Received registration request"); // 3. Add log statement
        AuthResponseDto response = authService.register(request);
        log.info("Successfully registered customer — customerId={}", response.customerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Authenticate customer credentials", description = "Validates customer email and password against stored BCrypt hashes and returns a Bearer JWT token upon successful authorization.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Customer authenticated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation error in request body payload", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid email or password credentials", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error occurred during login", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        log.info("Received login request"); // 4. Add log statement
        AuthResponseDto response = authService.login(request);
        log.info("Customer login successful");
        return ResponseEntity.ok(response);
    }
}