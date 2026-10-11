package com.transaction.dispute.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.config.TestSqsConfig;
import com.transaction.dispute.model.dto.AuthResponseDto;
import com.transaction.dispute.model.dto.DisputeRequestDto;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.model.entity.TransactionEntity;
import com.transaction.dispute.repository.AccountRepository;
import com.transaction.dispute.repository.TransactionRepository;
import com.transaction.dispute.service.AuthService;
import com.transaction.dispute.util.TestPayloadFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for /api/v1/disputes.
 *
 * The real DisputeService and repositories are wired against the H2 test database (the service is
 * NOT mocked), so every request runs the full controller -> service -> repository -> DB path,
 * including risk scoring, decision routing and the ownership/duplicate guards. Only the external
 * SqsClient is replaced (TestSqsConfig).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestSqsConfig.class)
class DisputeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ObjectMapper objectMapper;

    private String bearerToken;
    private AccountEntity account;

    @BeforeEach
    void setUp() {
        RegisterRequestDto request = TestPayloadFactory.createRegisterRequest();
        AuthResponseDto response = authService.register(request);
        bearerToken = "Bearer " + response.token();
        account = accountRepository.findByUserCustomerId(response.customerId()).orElseThrow();
    }

    private TransactionEntity saveOwnedTransaction() {
        return transactionRepository.save(
                TestPayloadFactory.createTransactionEntity(account, "TXN-" + UUID.randomUUID()));
    }

    // Verifies POST /api/v1/disputes returns 201 Created with dispute reference and decision outcome
    @Test
    @DisplayName("POST /api/v1/disputes should return 201 with dispute reference on valid request")
    void submitDispute_ShouldReturn201_WhenRequestIsValid() throws Exception {
        TransactionEntity tx = saveOwnedTransaction();
        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(tx.getTransactionId());

        mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disputeReference").exists())
                .andExpect(jsonPath("$.transactionId").value(tx.getTransactionId()))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.riskScore").isNumber())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // Verifies POST /api/v1/disputes returns 400 when required fields are missing
    @Test
    @DisplayName("POST /api/v1/disputes should return 400 when transactionId is missing")
    void submitDispute_ShouldReturn400_WhenTransactionIdIsMissing() throws Exception {
        String invalidJson = """
                { "reason": "UNAUTHORISED_MANDATE" }
                """;

        mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    // Verifies POST /api/v1/disputes returns 403 when no Authorization header is provided
    @Test
    @DisplayName("POST /api/v1/disputes should return 403 when no token is provided")
    void submitDispute_ShouldReturn403_WhenNoTokenProvided() throws Exception {
        TransactionEntity tx = saveOwnedTransaction();
        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(tx.getTransactionId());

        mockMvc.perform(post("/api/v1/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // Verifies GET /api/v1/disputes returns 200 with the customer's real dispute history
    @Test
    @DisplayName("GET /api/v1/disputes should return 200 with dispute history for authenticated customer")
    void getDisputeHistory_ShouldReturn200_WhenTokenIsValid() throws Exception {
        TransactionEntity tx = saveOwnedTransaction();
        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(tx.getTransactionId());

        // Create one real dispute first
        mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/disputes").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].disputeReference").exists())
                .andExpect(jsonPath("$.content[0].transactionId").value(tx.getTransactionId()));
    }

    // Verifies GET /api/v1/disputes returns 403 when no Authorization header is provided
    @Test
    @DisplayName("GET /api/v1/disputes should return 403 when no token is provided")
    void getDisputeHistory_ShouldReturn403_WhenNoTokenProvided() throws Exception {
        mockMvc.perform(get("/api/v1/disputes"))
                .andExpect(status().isForbidden());
    }

    // End-to-end ownership check: a second customer disputing the first customer's transaction is
    // mapped to HTTP 403 with a structured ErrorResponseDto. This runs the real ownership guard in
    // DisputeService (not a mock), confirming the ForbiddenException -> 403 mapping on a live POST.
    // **Validates: Requirements 2.1, 2.2**
    @Test
    @DisplayName("POST /api/v1/disputes should return 403 with structured error when disputing another customer's transaction")
    void submitDispute_ShouldReturn403_WhenTransactionNotOwned() throws Exception {
        // First customer's transaction (from setUp's account)
        TransactionEntity othersTransaction = saveOwnedTransaction();

        // A second, different customer authenticates and tries to dispute it
        AuthResponseDto other = authService.register(new RegisterRequestDto(
                "Thandeka", "Nkosi", "thandeka.nkosi@example.co.za", "Password123!"));
        String otherToken = "Bearer " + other.token();

        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(othersTransaction.getTransactionId());

        mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value(AppConstants.MSG_UNAUTHORIZED_TRANSACTION_ACCESS))
                .andExpect(jsonPath("$.path").exists());
    }
}