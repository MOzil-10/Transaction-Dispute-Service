package com.transaction.dispute.service;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.config.TestSqsConfig;
import com.transaction.dispute.exception.BadRequestException;
import com.transaction.dispute.exception.ForbiddenException;
import com.transaction.dispute.exception.ResourceNotFoundException;
import com.transaction.dispute.model.dto.DisputeRequestDto;
import com.transaction.dispute.model.dto.DisputeResponseDto;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.model.entity.DisputeEntity;
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

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@org.springframework.context.annotation.Import(TestSqsConfig.class)
class DisputeServiceTest {

    @Autowired private DisputeService disputeService;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private DisputeRepository disputeRepository;
    @Autowired private SqsClient sqsClient;

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

    // ---------------------------------------------------------------------------------------------
    // BUG CONDITION EXPLORATION TEST (Property 1 — Ownership Violation Returns 403 Forbidden)
    //
    // This test encodes the EXPECTED (fixed) behavior: an authenticated customer disputing a
    // transaction owned by a DIFFERENT, existing customer must trigger a ForbiddenException
    // (→ HTTP 403), NOT an UnauthorizedException (→ HTTP 401).
    //
    // isBugCondition(input): customerExists(customerId) AND transactionExists(transactionId)
    //                        AND ownerCustomerId(transactionId) != customerId
    //
    // On UNFIXED code this test MUST FAIL (ForbiddenException does not yet exist → compilation
    // failure; and the ownership check currently throws UnauthorizedException). The failure
    // CONFIRMS the bug. Do NOT fix the code or the test in this task.
    //
    // Counterexample: "Cross-customer dispute surfaces as UnauthorizedException / HTTP 401
    //                  instead of ForbiddenException / HTTP 403."
    // ---------------------------------------------------------------------------------------------
    // **Validates: Requirements 2.1, 2.2**
    @Test
    @DisplayName("Should throw ForbiddenException when customer disputes another customer transaction (bug condition — expects 403)")
    void submitDispute_ShouldThrowForbiddenException_WhenTransactionNotOwned() {
        // Register a second, existing customer (customerExists holds for the caller)
        RegisterRequestDto otherRequest = new RegisterRequestDto(
                "Thandeka", "Nkosi", "thandeka.nkosi@example.co.za", "Password123!");
        Long otherCustomerId = authService.register(otherRequest).customerId();

        // The second customer attempts to dispute the FIRST customer's transaction
        // (transactionExists holds; ownerCustomerId(transactionId) != otherCustomerId)
        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(savedTransaction.getTransactionId());

        // Expected (fixed) behavior: authorization failure → ForbiddenException with the
        // authorization message. This is the bug condition's correct outcome per Property 1.
        assertThatThrownBy(() -> disputeService.submitDispute(otherCustomerId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(AppConstants.MSG_UNAUTHORIZED_TRANSACTION_ACCESS);
    }

    // ---------------------------------------------------------------------------------------------
    // PRESERVATION TESTS (Property 2 — All Other Error Mappings and Success Paths Unchanged)
    //
    // These tests capture behavior observed on the UNFIXED production code for inputs where the
    // bug condition does NOT hold (isBugCondition(input) == false). They MUST PASS on unfixed
    // code and remain green after the fix, confirming the non-bug paths are untouched.
    //
    // Covered here:
    //   - Non-existent transaction  → ResourceNotFoundException (→ HTTP 404)   [Requirement 3.2]
    //   - Non-existent customer     → ResourceNotFoundException (→ HTTP 404)   [Requirement 3.3]
    //
    // Already covered elsewhere (confirmed, not duplicated):
    //   - Duplicate dispute         → BadRequestException (→ HTTP 400)  [3.4]
    //     (submitDispute_ShouldThrowBadRequestException_WhenDuplicateDisputeDetected)
    //   - Login/credential failure  → UnauthorizedException (→ HTTP 401) [3.1]
    //     (AuthServiceTest.login_ShouldThrowException_WhenPasswordIsIncorrect)
    //   - Valid owned dispute       → success + routing outcome (→ HTTP 201) [3.5]
    //     (submitDispute_ShouldPersistDisputeWithDecision_WhenTransactionIsEligible,
    //      submitDispute_ShouldRouteToManualInvestigation_ForHighRiskDispute,
    //      submitDispute_ShouldPersistSqsMessageId_AfterSuccessfulPublish)
    //   - Structured ErrorResponseDto shape for handled errors [3.6] is exercised by the
    //     GlobalExceptionHandler mapping; the exception-type assertions below pin the source
    //     signal (ResourceNotFoundException) that the handler maps to the 404 payload.
    // ---------------------------------------------------------------------------------------------
    // **Validates: Requirements 3.2, 3.3**

    // Preservation: a dispute for a transaction that does not exist must surface as
    // ResourceNotFoundException (→ HTTP 404), unchanged by the ownership-check fix.
    @Test
    @DisplayName("Preservation: Should throw ResourceNotFoundException when transaction does not exist")
    void submitDispute_ShouldThrowResourceNotFound_WhenTransactionDoesNotExist() {
        // Owning customer exists (customerExists holds), but the transaction does NOT exist,
        // so the lookup fails with 404 BEFORE the ownership check — not the bug condition.
        Long nonExistentTransactionId = 9_999_999L;
        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(nonExistentTransactionId);

        assertThatThrownBy(() -> disputeService.submitDispute(customerId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(AppConstants.MSG_TRANSACTION_NOT_FOUND + nonExistentTransactionId);
    }

    // Preservation: a dispute submitted by a customer that does not exist must surface as
    // ResourceNotFoundException (→ HTTP 404), unchanged by the ownership-check fix.
    @Test
    @DisplayName("Preservation: Should throw ResourceNotFoundException when customer does not exist")
    void submitDispute_ShouldThrowResourceNotFound_WhenCustomerDoesNotExist() {
        // The authenticated customerId does NOT resolve, so the customer lookup fails with 404
        // BEFORE the transaction lookup and ownership check — not the bug condition.
        Long nonExistentCustomerId = 8_888_888L;
        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(savedTransaction.getTransactionId());

        assertThatThrownBy(() -> disputeService.submitDispute(nonExistentCustomerId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(AppConstants.MSG_USER_NOT_FOUND_ID + nonExistentCustomerId);
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

    // Verifies sqsMessageId is persisted on the DisputeEntity after successful SQS publish
    @Test
    @DisplayName("Should persist sqsMessageId on dispute after SQS publish")
    void submitDispute_ShouldPersistSqsMessageId_AfterSuccessfulPublish() {
        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(savedTransaction.getTransactionId());

        DisputeResponseDto response = disputeService.submitDispute(customerId, request);

        DisputeEntity persisted = disputeRepository.findAll().stream()
                .filter(d -> d.getDisputeReference().equals(response.disputeReference()))
                .findFirst()
                .orElseThrow();

        // TestSqsConfig mock returns "test-sqs-message-id"
        assertThat(persisted.getSqsMessageId()).isEqualTo("test-sqs-message-id");
    }

    // ---------------------------------------------------------------------------------------------
    // PRESERVATION COVERAGE MAP — Requirement 10: Preservation of Existing Dispute Behavior
    //
    // This class is the behavioral guard that the regulatory-eligibility feature (window + category
    // guards, auto-approve refund) adds enforcement WITHOUT regressing existing dispute behavior.
    // Fixtures now carry a disputable category (GYM_MEMBERSHIP) and an in-window transactionDate
    // (now()), so every pre-existing path below still exercises success/error exactly as before.
    //
    //   10.1 ownership → 403 Forbidden
    //        submitDispute_ShouldThrowForbiddenException_WhenTransactionNotOwned
    //   10.2 duplicate dispute → 400 Bad Request
    //        submitDispute_ShouldThrowBadRequestException_WhenDuplicateDisputeDetected
    //   10.3 transaction/customer not found → 404 Not Found
    //        submitDispute_ShouldThrowResourceNotFound_WhenTransactionDoesNotExist
    //        submitDispute_ShouldThrowResourceNotFound_WhenCustomerDoesNotExist
    //   10.4 eligible submission → 201 Created with routing outcome
    //        submitDispute_ShouldPersistDisputeWithDecision_WhenTransactionIsEligible
    //        submitDispute_ShouldRouteToManualInvestigation_ForHighRiskDispute
    //   10.5 SQS publish failure is non-fatal (request still succeeds)
    //        submitDispute_ShouldPersistSqsMessageId_AfterSuccessfulPublish (publish succeeds path)
    //        submitDispute_ShouldCompleteSuccessfully_WhenSqsPublishFails    (publish fails path)
    // ---------------------------------------------------------------------------------------------
    // **Validates: Requirements 10.1, 10.2, 10.3, 10.4, 10.5**

    // Preservation (Req 10.5): when the SQS publish fails, the dispute submission must still
    // complete successfully (no exception, dispute persisted) with sqsMessageId left null —
    // confirming the publish is non-fatal to the request.
    @Test
    @DisplayName("Preservation: Should complete dispute submission when SQS publish fails (non-fatal)")
    void submitDispute_ShouldCompleteSuccessfully_WhenSqsPublishFails() {
        // Force the SQS client (TestSqsConfig Mockito mock) to fail on publish for this test.
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenThrow(new RuntimeException("Simulated SQS outage"));

        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(savedTransaction.getTransactionId());

        DisputeResponseDto response = disputeService.submitDispute(customerId, request);

        // Request succeeds despite the publish failure (the 201 path is intact)
        assertThat(response).isNotNull();
        assertThat(response.disputeReference()).startsWith("DSP-");
        assertThat(response.status()).isIn(DisputeStatus.AUTO_APPROVED, DisputeStatus.QUEUED_FOR_AGENT);

        // Dispute is persisted, but sqsMessageId stays null because the publish failed
        DisputeEntity persisted = disputeRepository.findAll().stream()
                .filter(d -> d.getDisputeReference().equals(response.disputeReference()))
                .findFirst()
                .orElseThrow();
        assertThat(persisted.getSqsMessageId()).isNull();

        // A second eligible submission still throws nothing fatal from the SQS layer either
        assertThatCode(() -> {
            AccountEntity account = accountRepository.findByUserCustomerId(customerId).orElseThrow();
            TransactionEntity another = transactionRepository.save(
                    TestPayloadFactory.createTransactionEntity(account, "TXN-" + UUID.randomUUID()));
            disputeService.submitDispute(customerId,
                    TestPayloadFactory.createDisputeRequest(another.getTransactionId()));
        }).doesNotThrowAnyException();
    }
}