package com.transaction.dispute.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.config.TestSqsConfig;
import com.transaction.dispute.model.dto.AuthResponseDto;
import com.transaction.dispute.model.dto.DisputeRequestDto;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.model.entity.TransactionEntity;
import com.transaction.dispute.model.enums.TransactionCategory;
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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature: regulatory-dispute-eligibility
 *
 * Web-layer confirmation that the transaction-category eligibility rule is enforced end-to-end,
 * all the way out through the REST API. Unlike {@code DisputeControllerTest}, this test does NOT
 * mock {@code DisputeService} — it wires the real service into the Spring context against the H2
 * test database so the category guard actually fires on a live POST. The transactions are dated
 * {@code now()} so they always sit inside the 60-day window under the real clock, isolating the
 * category rule as the only thing that can block (or allow) the submission.
 *
 * **Validates: Requirements 4.3, 9.2**
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestSqsConfig.class)
class DisputeCategoryControllerTest {

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

    /**
     * Persist a transaction owned by the registered customer with the given category and an
     * in-window ({@code now()}) transaction date. A unique reference avoids the unique-constraint
     * and duplicate-dispute guards interfering across tests.
     */
    private TransactionEntity saveTransaction(TransactionCategory category) {
        TransactionEntity transaction =
                TestPayloadFactory.createTransactionEntity(account, "TXN-" + UUID.randomUUID());
        transaction.setCategory(category);
        transaction.setTransactionDate(OffsetDateTime.now());
        return transactionRepository.save(transaction);
    }

    // Verifies a direct API call to dispute a NON-disputable transaction returns HTTP 400 with the
    // category-not-disputable message, proving the server-side rule cannot be bypassed via the API.
    // **Validates: Requirements 4.3, 9.2**
    @Test
    @DisplayName("POST /api/v1/disputes should return 400 when the transaction category is non-disputable")
    void submitDispute_ShouldReturn400_WhenCategoryNotDisputable() throws Exception {
        TransactionEntity transaction = saveTransaction(TransactionCategory.IN_STORE_RETAIL);
        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(transaction.getTransactionId());

        mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(AppConstants.MSG_CATEGORY_NOT_DISPUTABLE));
    }

    // Happy path: a disputable category still submits successfully through the real service,
    // proving the category guard does not regress the eligible-dispute flow at the web layer.
    // **Validates: Requirements 4.1**
    @Test
    @DisplayName("POST /api/v1/disputes should return 201 when the transaction category is disputable")
    void submitDispute_ShouldReturn201_WhenCategoryDisputable() throws Exception {
        TransactionEntity transaction = saveTransaction(TransactionCategory.LOAN);
        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(transaction.getTransactionId());

        mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disputeReference").exists())
                .andExpect(jsonPath("$.transactionId").value(transaction.getTransactionId()));
    }
}
