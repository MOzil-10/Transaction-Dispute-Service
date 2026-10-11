package com.transaction.dispute.controller;

import com.transaction.dispute.config.TestSqsConfig;
import com.transaction.dispute.model.dto.AuthResponseDto;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.model.entity.TransactionEntity;
import com.transaction.dispute.model.enums.MandateType;
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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for GET /api/v1/transactions/search: the real service is wired (not mocked),
 * confirming a 200 page is returned and that an invalid mandateType yields a 400 via the
 * MethodArgumentTypeMismatch handler.
 *
 * Validates the new filtered transaction-search endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestSqsConfig.class)
class TransactionSearchControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        RegisterRequestDto request = TestPayloadFactory.createRegisterRequest();
        AuthResponseDto response = authService.register(request);
        bearerToken = "Bearer " + response.token();

        AccountEntity account = accountRepository.findByUserCustomerId(response.customerId()).orElseThrow();
        TransactionEntity t = TestPayloadFactory.createTransactionEntity(account, "TXN-" + UUID.randomUUID());
        t.setMandateType(MandateType.DEBICHECK);
        t.setTransactionDate(OffsetDateTime.now());
        transactionRepository.save(t);
    }

    @Test
    @DisplayName("GET /search returns 200 with a page (no filters)")
    void search_ShouldReturn200_NoFilters() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/search").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /search?mandateType=DEBICHECK returns 200 and the matching transaction")
    void search_ShouldReturn200_WithMandateFilter() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/search")
                        .param("mandateType", "DEBICHECK")
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].mandateType").value("DEBICHECK"));
    }

    @Test
    @DisplayName("GET /search with an invalid mandateType returns 400")
    void search_ShouldReturn400_WhenMandateTypeInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/search")
                        .param("mandateType", "NOT_A_TYPE")
                        .header("Authorization", bearerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("GET /search with fromDate after toDate returns 400")
    void search_ShouldReturn400_WhenDateRangeInverted() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/search")
                        .param("fromDate", "2026-03-20")
                        .param("toDate", "2026-03-01")
                        .header("Authorization", bearerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}