package com.transaction.dispute.controller;

import com.transaction.dispute.config.TestSqsConfig;
import com.transaction.dispute.exception.ResourceNotFoundException;
import com.transaction.dispute.model.dto.AccountResponseDto;
import com.transaction.dispute.model.dto.AuthResponseDto;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.service.AccountService;
import com.transaction.dispute.service.AuthService;
import com.transaction.dispute.util.TestPayloadFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@org.springframework.context.annotation.Import(TestSqsConfig.class)
class AccountControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;

    @MockBean
    private AccountService accountService;

    private String obtainBearerToken() {
        RegisterRequestDto request = TestPayloadFactory.createRegisterRequest();
        AuthResponseDto response = authService.register(request);
        return "Bearer " + response.token();
    }

    // Verifies GET /api/v1/account returns 200 with the account overview for an authenticated customer
    @Test
    @DisplayName("GET /api/v1/account should return 200 with account overview for authenticated customer")
    void getAccountOverview_ShouldReturn200_WhenTokenIsValid() throws Exception {
        AccountResponseDto dto = new AccountResponseDto(
                1000001L, "Trevor", "Sesoko", "trevor.sesoko@example.co.za",
                "5000001", "MAIN_ACCOUNT", new BigDecimal("12450.00"), "ZAR");
        when(accountService.getAccountOverview(anyLong())).thenReturn(dto);

        String token = obtainBearerToken();

        mockMvc.perform(get("/api/v1/account").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Trevor"))
                .andExpect(jsonPath("$.lastName").value("Sesoko"))
                .andExpect(jsonPath("$.accountNumber").value("5000001"))
                .andExpect(jsonPath("$.accountType").value("MAIN_ACCOUNT"))
                .andExpect(jsonPath("$.balance").value(12450.00))
                .andExpect(jsonPath("$.currency").value("ZAR"));
    }

    // Verifies GET /api/v1/account returns 403 when no Authorization header is provided
    @Test
    @DisplayName("GET /api/v1/account should return 403 when no token is provided")
    void getAccountOverview_ShouldReturn403_WhenNoTokenProvided() throws Exception {
        mockMvc.perform(get("/api/v1/account"))
                .andExpect(status().isForbidden());
    }

    // Verifies GET /api/v1/account returns 404 when the customer has no account
    @Test
    @DisplayName("GET /api/v1/account should return 404 when account not found")
    void getAccountOverview_ShouldReturn404_WhenAccountNotFound() throws Exception {
        when(accountService.getAccountOverview(anyLong()))
                .thenThrow(new ResourceNotFoundException("Account not found for customer ID: 99999"));

        String token = obtainBearerToken();

        mockMvc.perform(get("/api/v1/account").header("Authorization", token))
                .andExpect(status().isNotFound());
    }
}