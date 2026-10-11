package com.transaction.dispute.controller;

import com.transaction.dispute.config.TestSqsConfig;
import com.transaction.dispute.model.dto.AuthResponseDto;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for GET /api/v1/transactions.
 *
 * The real TransactionService and repositories are wired against the H2 test database (the service
 * is NOT mocked), so each request exercises the full controller -> service -> repository -> DB path.
 * Only the external SqsClient is replaced (TestSqsConfig).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestSqsConfig.class)
class TransactionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;

    private String bearerToken;
    private AccountEntity account;

    @BeforeEach
    void setUp() {
        RegisterRequestDto request = TestPayloadFactory.createRegisterRequest();
        AuthResponseDto response = authService.register(request);
        bearerToken = "Bearer " + response.token();
        account = accountRepository.findByUserCustomerId(response.customerId()).orElseThrow();
    }

    // Verifies GET /api/v1/transactions returns 200 and the persisted transaction for the customer
    @Test
    @DisplayName("GET /api/v1/transactions should return 200 with the customer's transactions")
    void getTransactions_ShouldReturn200_WhenTokenIsValid() throws Exception {
        TransactionEntity tx = transactionRepository.save(
                TestPayloadFactory.createTransactionEntity(account, "TXN-" + UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/transactions").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].transactionId").value(tx.getTransactionId()))
                .andExpect(jsonPath("$.content[0].reference").value(tx.getReference()))
                .andExpect(jsonPath("$.content[0].merchant").value(tx.getMerchant()));
    }

    // Verifies GET /api/v1/transactions returns 403 when no Authorization header is provided
    @Test
    @DisplayName("GET /api/v1/transactions should return 403 when no Bearer token is provided")
    void getTransactions_ShouldReturn403_WhenNoTokenProvided() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isForbidden());
    }

    // Verifies GET /api/v1/transactions returns an empty page when the customer has no transactions
    @Test
    @DisplayName("GET /api/v1/transactions should return empty page when customer has no transactions")
    void getTransactions_ShouldReturnEmptyPage_WhenNoTransactionsExist() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}