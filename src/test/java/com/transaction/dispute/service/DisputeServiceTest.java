package com.transaction.dispute.service;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.exception.BadRequestException;
import com.transaction.dispute.exception.ResourceNotFoundException;
import com.transaction.dispute.exception.UnauthorizedException;
import com.transaction.dispute.model.dto.DisputeRequestDto;
import com.transaction.dispute.model.dto.DisputeResponseDto;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.model.entity.TransactionEntity;
import com.transaction.dispute.model.enums.DecisionRoute;
import com.transaction.dispute.model.enums.DisputeReason;
import com.transaction.dispute.model.enums.DisputeStatus;
import com.transaction.dispute.repository.AccountRepository;
import com.transaction.dispute.repository.DisputeRepository;
import com.transaction.dispute.repository.TransactionRepository;
import com.transaction.dispute.repository.UserRepository;
import com.transaction.dispute.util.TestPayloadFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DisputeServiceTest {

    @Autowired private DisputeService disputeService;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private DisputeRepository disputeRepository;

    private Long customerId;
    private TransactionEntity savedTransaction;

    @BeforeEach
    void setUp() {
        disputeRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        RegisterRequestDto request = TestPayloadFactory.createRegisterRequest();
        customerId = authService.register(request).customerId();

        AccountEntity account = accountRepository.findByUserCustomerId(customerId).orElseThrow();
        savedTransaction = transactionRepository.save(
                TestPayloadFactory.createTransactionEntity(account, "TXN-" + UUID.randomUUID()));
    }

    // Verifies a valid dispute is persisted with a risk score, decision route, and correct status
    @Test
    @DisplayName("Should persist dispute with risk score and decision route on valid submission")
    void submitDispute_ShouldPersistDisputeWithDecision_WhenTransactionIsEligible() {
        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(savedTransaction.getTransactionId());

        DisputeResponseDto response = disputeService.submitDispute(customerId, request);

        assertThat(response).isNotNull();
        assertThat(response.disputeId()).isNotNull();
        assertThat(response.disputeReference()).startsWith("DSP-");
        assertThat(response.riskScore()).isPositive();
        assertThat(response.decisionRoute()).isNotNull();
        assertThat(response.status()).isIn(DisputeStatus.AUTO_APPROVED, DisputeStatus.QUEUED_FOR_AGENT);
        assertThat(response.message()).isNotBlank();
    }

    // Verifies MANUAL_INVESTIGATION is routed correctly for STANDARD_EFT + UNAUTHORISED_MANDATE
    @Test
    @DisplayName("Should route to MANUAL_INVESTIGATION for high-risk STANDARD_EFT + UNAUTHORISED_MANDATE")
    void submitDispute_ShouldRouteToManualInvestigation_ForHighRiskDispute() {
        // STANDARD_EFT (50) + UNAUTHORISED_MANDATE (20) = 70 → MANUAL_INVESTIGATION
        DisputeRequestDto request = new DisputeRequestDto(
                savedTransaction.getTransactionId(),
                DisputeReason.UNAUTHORISED_MANDATE,
                "Unexpected debit order"
        );

        DisputeResponseDto response = disputeService.submitDispute(customerId, request);

        assertThat(response.decisionRoute()).isEqualTo(DecisionRoute.MANUAL_INVESTIGATION);
        assertThat(response.status()).isEqualTo(DisputeStatus.QUEUED_FOR_AGENT);
        assertThat(response.message()).isEqualTo(AppConstants.MSG_DISPUTE_QUEUED_AGENT);
    }

    // Verifies BadRequestException is thrown when a dispute already exists for the same transaction
    @Test
    @DisplayName("Should throw BadRequestException when dispute already exists for transaction")
    void submitDispute_ShouldThrowBadRequestException_WhenDuplicateDisputeDetected() {
        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(savedTransaction.getTransactionId());
        disputeService.submitDispute(customerId, request);

        assertThatThrownBy(() -> disputeService.submitDispute(customerId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(AppConstants.MSG_DISPUTE_ALREADY_EXISTS);
    }

    // Verifies UnauthorizedException when a customer attempts to dispute another customer's transaction
    @Test
    @DisplayName("Should throw UnauthorizedException when customer disputes another customer transaction")
    void submitDispute_ShouldThrowUnauthorizedException_WhenTransactionNotOwned() {
        // Register a second customer
        RegisterRequestDto otherRequest = new RegisterRequestDto(
                "Bongani", "Dlamini", "bongani.dlamini@example.co.za", "Password123!");
        Long otherCustomerId = authService.register(otherRequest).customerId();

        // Try to dispute the first customer transaction using the second customer ID
        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(savedTransaction.getTransactionId());

        assertThatThrownBy(() -> disputeService.submitDispute(otherCustomerId, request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(AppConstants.MSG_UNAUTHORIZED_TRANSACTION_ACCESS);
    }

    // Verifies paginated dispute history is returned for a customer with submitted disputes
    @Test
    @DisplayName("Should return paginated dispute history for customer")
    void getDisputeHistory_ShouldReturnPagedDisputes_WhenDisputesExist() {
        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(savedTransaction.getTransactionId());
        disputeService.submitDispute(customerId, request);

        Page<DisputeResponseDto> history = disputeService.getDisputeHistory(customerId, PageRequest.of(0, 10));

        assertThat(history).isNotNull();
        assertThat(history.getTotalElements()).isEqualTo(1);
        assertThat(history.getContent().getFirst().disputeReference()).startsWith("DSP-");
    }

    // Verifies an empty page is returned when the customer has no disputes
    @Test
    @DisplayName("Should return empty page when customer has no disputes")
    void getDisputeHistory_ShouldReturnEmptyPage_WhenNoDisputesExist() {
        Page<DisputeResponseDto> history = disputeService.getDisputeHistory(customerId, PageRequest.of(0, 10));

        assertThat(history).isNotNull();
        assertThat(history.getTotalElements()).isZero();
    }
}

