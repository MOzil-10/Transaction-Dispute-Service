package com.transaction.dispute.controller;

import com.transaction.dispute.config.TestSqsConfig;
import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.model.dto.AuthResponseDto;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.repository.AccountRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for GET /api/v1/account.
 *
 * The real AccountService and repositories are wired against the H2 test database (the service is
 * NOT mocked). Registration provisions a real customer + main account, so the overview is asserted
 * against actually-persisted data, exercising controller -> service -> repository -> DB.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestSqsConfig.class)
class AccountControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private AccountRepository accountRepository;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        RegisterRequestDto request = TestPayloadFactory.createRegisterRequest();
        AuthResponseDto response = authService.register(request);
        bearerToken = "Bearer " + response.token();
    }

    // Verifies GET /api/v1/account returns 200 with the real provisioned account for the customer
    @Test
    @DisplayName("GET /api/v1/account should return 200 with the authenticated customer's account overview")
    void getAccountOverview_ShouldReturn200_WhenTokenIsValid() throws Exception {
        mockMvc.perform(get("/api/v1/account").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Rita"))
                .andExpect(jsonPath("$.lastName").value("Sithole"))
                .andExpect(jsonPath("$.email").value("rita.sithole@example.co.za"))
                .andExpect(jsonPath("$.accountType").value(AppConstants.DEFAULT_ACCOUNT_TYPE))
                .andExpect(jsonPath("$.currency").value(AppConstants.DEFAULT_CURRENCY))
                .andExpect(jsonPath("$.accountNumber").isNotEmpty())
                .andExpect(jsonPath("$.balance").isNumber());
    }

    // Verifies GET /api/v1/account returns 403 when no Authorization header is provided
    @Test
    @DisplayName("GET /api/v1/account should return 403 when no token is provided")
    void getAccountOverview_ShouldReturn403_WhenNoTokenProvided() throws Exception {
        mockMvc.perform(get("/api/v1/account"))
                .andExpect(status().isForbidden());
    }

    // Verifies GET /api/v1/account returns 404 when the authenticated customer has no account.
    // The provisioned account is removed first so the real not-found path fires end-to-end.
    @Test
    @DisplayName("GET /api/v1/account should return 404 when the customer has no account")
    void getAccountOverview_ShouldReturn404_WhenAccountNotFound() throws Exception {
        accountRepository.deleteAll();
        accountRepository.flush();

        mockMvc.perform(get("/api/v1/account").header("Authorization", bearerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}