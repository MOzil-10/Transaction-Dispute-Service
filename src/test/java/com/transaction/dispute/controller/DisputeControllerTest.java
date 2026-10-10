package com.transaction.dispute.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transaction.dispute.config.TestSqsConfig;
import com.transaction.dispute.model.dto.AuthResponseDto;
import com.transaction.dispute.model.dto.DisputeRequestDto;
import com.transaction.dispute.model.dto.DisputeResponseDto;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.model.enums.DisputeReason;
import com.transaction.dispute.service.AuthService;
import com.transaction.dispute.service.DisputeService;
import com.transaction.dispute.util.TestPayloadFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@org.springframework.context.annotation.Import(TestSqsConfig.class)
class DisputeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private ObjectMapper objectMapper;

    @MockBean
    private DisputeService disputeService;

    private String obtainBearerToken() {
        RegisterRequestDto request = TestPayloadFactory.createRegisterRequest();
        AuthResponseDto response = authService.register(request);
        return "Bearer " + response.token();
    }

    // Verifies POST /api/v1/disputes returns 201 Created with dispute reference and decision outcome
    @Test
    @DisplayName("POST /api/v1/disputes should return 201 with dispute reference on valid request")
    void submitDispute_ShouldReturn201_WhenRequestIsValid() throws Exception {
        DisputeResponseDto dto = TestPayloadFactory.createDisputeResponseDto();
        when(disputeService.submitDispute(anyLong(), any(DisputeRequestDto.class))).thenReturn(dto);

        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(5000102L);
        String token = obtainBearerToken();

        mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disputeReference").value(dto.disputeReference()))
                .andExpect(jsonPath("$.status").value(dto.status().name()))
                .andExpect(jsonPath("$.riskScore").value(dto.riskScore()))
                .andExpect(jsonPath("$.message").value(dto.message()));
    }

    // Verifies POST /api/v1/disputes returns 400 when required fields are missing
    @Test
    @DisplayName("POST /api/v1/disputes should return 400 when transactionId is missing")
    void submitDispute_ShouldReturn400_WhenTransactionIdIsMissing() throws Exception {
        String invalidJson = """
                { "reason": "UNAUTHORISED_MANDATE" }
                """;
        String token = obtainBearerToken();

        mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    // Verifies POST /api/v1/disputes returns 403 when no Authorization header is provided
    @Test
    @DisplayName("POST /api/v1/disputes should return 403 when no token is provided")
    void submitDispute_ShouldReturn403_WhenNoTokenProvided() throws Exception {
        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(5000102L);

        mockMvc.perform(post("/api/v1/disputes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // Verifies GET /api/v1/disputes returns 200 with paginated dispute history for authenticated customer
    @Test
    @DisplayName("GET /api/v1/disputes should return 200 with dispute history for authenticated customer")
    void getDisputeHistory_ShouldReturn200_WhenTokenIsValid() throws Exception {
        DisputeResponseDto dto = TestPayloadFactory.createDisputeResponseDto();
        when(disputeService.getDisputeHistory(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto)));

        String token = obtainBearerToken();

        mockMvc.perform(get("/api/v1/disputes")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].disputeReference").value(dto.disputeReference()))
                .andExpect(jsonPath("$.content[0].status").value(dto.status().name()));
    }

    // Verifies GET /api/v1/disputes returns 403 when no Authorization header is provided
    @Test
    @DisplayName("GET /api/v1/disputes should return 403 when no token is provided")
    void getDisputeHistory_ShouldReturn403_WhenNoTokenProvided() throws Exception {
        mockMvc.perform(get("/api/v1/disputes"))
                .andExpect(status().isForbidden());
    }
}

