package com.transaction.dispute.controller;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.model.dto.AuthResponseDto;
import com.transaction.dispute.model.dto.LoginRequestDto;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.service.AuthService;
import com.transaction.dispute.util.TestPayloadFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.context.annotation.Import(com.transaction.dispute.config.TestSqsConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    // Inline JSON Samples for Controller Request Payloads
    private static final String VALID_REGISTER_JSON = """
            {
              "firstName": "Rita",
              "lastName": "Sithole",
              "email": "rita.sithole@example.co.za",
              "password": "Password123!"
            }
            """;

    private static final String INVALID_EMAIL_REGISTER_JSON = """
            {
              "firstName": "Rita",
              "lastName": "Sithole",
              "email": "invalid-email-format",
              "password": "Password123!"
            }
            """;

    private static final String VALID_LOGIN_JSON = """
            {
              "email": "rita.sithole@example.co.za",
              "password": "Password123!"
            }
            """;

    // This test verifies that POST /api/v1/auth/register parses an inline JSON sample payload
    // and returns HTTP 201 Created with a valid AuthResponse structure.
    @Test
    @DisplayName("POST /api/v1/auth/register should return HTTP 201 Created on valid request JSON")
    void register_ShouldReturnHttp201_WhenPayloadIsValidJson() throws Exception {
        // Given
        AuthResponseDto response = TestPayloadFactory.createAuthResponse();
        when(authService.register(any(RegisterRequestDto.class))).thenReturn(response);

        // When / Then
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(response.token()))
                .andExpect(jsonPath("$.customerId").value(response.customerId()))
                .andExpect(jsonPath("$.email").value(response.email()));
    }

    // This test verifies that submitting an invalid email JSON string to /register
    // triggers a 400 Bad Request error response handled by the GlobalExceptionHandler.
    @Test
    @DisplayName("POST /api/v1/auth/register should return HTTP 400 Bad Request on invalid email JSON")
    void register_ShouldReturnHttp400_WhenEmailIsInvalidInJson() throws Exception {
        // When / Then
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVALID_EMAIL_REGISTER_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(AppConstants.MSG_VALIDATION_FAILED));
    }

    // This test verifies that POST /api/v1/auth/login processes an inline JSON login sample payload
    // and returns HTTP 200 OK along with the JWT access token.
    @Test
    @DisplayName("POST /api/v1/auth/login should return HTTP 200 OK on valid credentials JSON")
    void login_ShouldReturnHttp200_WhenCredentialsAreValidJson() throws Exception {
        // Given
        AuthResponseDto response = TestPayloadFactory.createAuthResponse();
        when(authService.login(any(LoginRequestDto.class))).thenReturn(response);

        // When / Then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOGIN_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(response.token()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }
}