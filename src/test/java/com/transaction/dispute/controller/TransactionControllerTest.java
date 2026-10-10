package com.transaction.dispute.controller;

import com.transaction.dispute.model.dto.AuthResponseDto;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.model.dto.TransactionResponseDto;
import com.transaction.dispute.service.AuthService;
import com.transaction.dispute.service.TransactionService;
import com.transaction.dispute.util.TestPayloadFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @MockBean
    private TransactionService transactionService;

    // Registers a real customer and logs in to obtain a valid JWT for authenticated requests
    private String obtainBearerToken() {
        RegisterRequestDto request = TestPayloadFactory.createRegisterRequest();
        AuthResponseDto response = authService.register(request);
        return "Bearer " + response.token();
    }

    // Verifies GET /api/v1/transactions returns 200 and transaction content for an authenticated customer
    @Test
    @DisplayName("GET /api/v1/transactions should return 200 with transaction page when JWT is valid")
    void getTransactions_ShouldReturn200_WhenTokenIsValid() throws Exception {
        // Given
        TransactionResponseDto dto = TestPayloadFactory.createTransactionResponseDto();
        when(transactionService.getTransactionPage(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto)));

        String token = obtainBearerToken();

        // When / Then
        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transactionId").value(dto.transactionId()))
                .andExpect(jsonPath("$.content[0].reference").value(dto.reference()))
                .andExpect(jsonPath("$.content[0].merchant").value(dto.merchant()));
    }

    // Verifies GET /api/v1/transactions returns 401 when no Authorization header is provided
    @Test
    @DisplayName("GET /api/v1/transactions should return 403 when no Bearer token is provided")
    void getTransactions_ShouldReturn403_WhenNoTokenProvided() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isForbidden());
    }

    // Verifies GET /api/v1/transactions returns empty content when no transactions exist
    @Test
    @DisplayName("GET /api/v1/transactions should return empty page when customer has no transactions")
    void getTransactions_ShouldReturnEmptyPage_WhenNoTransactionsExist() throws Exception {
        // Given
        when(transactionService.getTransactionPage(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        String token = obtainBearerToken();

        // When / Then
        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}

