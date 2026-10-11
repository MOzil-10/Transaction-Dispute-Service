package com.transaction.dispute.service;

import com.transaction.dispute.config.TestSqsConfig;
import com.transaction.dispute.exception.BadRequestException;
import com.transaction.dispute.model.dto.DisputeRequestDto;
import com.transaction.dispute.model.dto.DisputeResponseDto;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.model.entity.DisputeEntity;
import com.transaction.dispute.model.entity.TransactionEntity;
import com.transaction.dispute.model.enums.DecisionRoute;
import com.transaction.dispute.model.enums.DisputeReason;
import com.transaction.dispute.model.enums.DisputeStatus;
import com.transaction.dispute.model.enums.MandateType;
import com.transaction.dispute.model.enums.TransactionCategory;
import com.transaction.dispute.model.enums.TransactionStatus;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feature: Regulatory Dispute Eligibility — refund-path coverage.
 *
 * Exercises Properties 7, 8 and 9 (tasks 7.7–7.9) which all drive the auto-approve
 * refund path on {@link DisputeService}. They share one class because they operate on
 * the same service and persistence surface.
 *
 * Implementation note on jqwik-in-Spring: jqwik {@code @Property} methods do not get a
 * clean per-try Spring/{@code @Transactional} lifecycle (a single JVM method invocation
 * runs many tries, so Spring's test-managed rollback and the shared H2 state would not
 * reset between tries, and injected beans are not re-provisioned per try). Mixing that
 * with {@code @SpringBootTest} injection is unreliable, so — as the task permits — these
 * are implemented as JUnit example tests over a few representative {@link BigDecimal}
 * amounts (0.01, 450.00, 999999.99). Each test is annotated {@code @Transactional} so the
 * Spring test framework rolls the H2 row changes back after every method, giving each
 * example a clean slate. The property each example covers is documented inline.
 *
 * Clock: the production {@code TimeConfig} real SAST clock is used as-is. All transactions
 * are dated {@code OffsetDateTime.now()} (0 days elapsed → inside the 60-day window), so no
 * fixed clock is required here.
 *
 * Risk scoring reminder (to force decision routes deterministically):
 *   RiskScoreEngine base: DEBICHECK = 30, otherwise = 50.
 *   Reason increments: UNAUTHORISED_MANDATE +20, DUPLICATE_DEBIT/CANCELLED_CONTRACT +15,
 *                      INCORRECT_AMOUNT +10, INCORRECT_DATE +5.
 *   DecisionMatrix: score <= 50 → AUTO_APPROVE, else MANUAL_INVESTIGATION.
 *   AUTO_APPROVE example  → DEBICHECK (30) + INCORRECT_DATE (5)      = 35  (<= 50).
 *   MANUAL example        → STANDARD_EFT (50) + UNAUTHORISED_MANDATE (20) = 70 (>  50).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(TestSqsConfig.class)
class DisputeRefundPropertyTest {

    @Autowired private DisputeService disputeService;
    @Autowired private AuthService authService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private DisputeRepository disputeRepository;
    @Autowired private UserRepository userRepository;

    // Representative amounts standing in for the jqwik @ForAll BigDecimal generation:
    // a boundary minimum, a typical value, and a large value.
    private static final BigDecimal[] AMOUNTS = {
            new BigDecimal("0.01"),
            new BigDecimal("450.00"),
            new BigDecimal("999999.99")
    };

    private Long customerId;
    private AccountEntity account;

    @BeforeEach
    void setUp() {
        disputeRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        RegisterRequestDto request = TestPayloadFactory.createRegisterRequest();
        customerId = authService.register(request).customerId();
        account = accountRepository.findByUserCustomerId(customerId).orElseThrow();
    }

    // ------------------------------------------------------------------------------------------
    // Local builders — each test persists a fresh account balance and a transaction whose
    // mandate type drives the risk score into the desired decision route. Category is a
    // disputable one (GYM_MEMBERSHIP) and the date is now() so the window/category guards pass.
    // ------------------------------------------------------------------------------------------

    /** AUTO_APPROVE: DEBICHECK (base 30) + INCORRECT_DATE (+5) = 35 (<= 50). */
    private TransactionEntity persistAutoApproveTransaction(BigDecimal startingBalance, BigDecimal amount) {
        account.setBalance(startingBalance);
        accountRepository.save(account);
        return transactionRepository.save(TransactionEntity.builder()
                .account(account)
                .reference("TXN-" + UUID.randomUUID())
                .merchant("FITNESS_CLUB_ZA")
                .amount(amount)
                .currency("ZAR")
                .mandateType(MandateType.DEBICHECK)
                .category(TransactionCategory.GYM_MEMBERSHIP)
                .status(TransactionStatus.COMPLETED)
                .transactionDate(OffsetDateTime.now())
                .build());
    }

    /** MANUAL_INVESTIGATION: STANDARD_EFT (base 50) + UNAUTHORISED_MANDATE (+20) = 70 (> 50). */
    private TransactionEntity persistManualInvestigationTransaction(BigDecimal startingBalance, BigDecimal amount) {
        account.setBalance(startingBalance);
        accountRepository.save(account);
        return transactionRepository.save(TransactionEntity.builder()
                .account(account)
                .reference("TXN-" + UUID.randomUUID())
                .merchant("FITNESS_CLUB_ZA")
                .amount(amount)
                .currency("ZAR")
                .mandateType(MandateType.STANDARD_EFT)
                .category(TransactionCategory.GYM_MEMBERSHIP)
                .status(TransactionStatus.COMPLETED)
                .transactionDate(OffsetDateTime.now())
                .build());
    }

    private DisputeEntity reloadDispute(String disputeReference) {
        return disputeRepository.findAll().stream()
                .filter(d -> d.getDisputeReference().equals(disputeReference))
                .findFirst()
                .orElseThrow();
    }

    // ==========================================================================================
    // Property 7 (task 7.7) — Auto-approved refund credits balance and records audit.
    //
    // For every eligible AUTO_APPROVE dispute: newBalance == startingBalance + amount,
    // dispute.refundedAmount == amount, dispute.refundedAt != null.
    // **Validates: Requirements 6.1, 8.1, 8.2**
    // ==========================================================================================
    @Test
    @DisplayName("Property 7: auto-approved refund credits balance by the transaction amount and records audit fields")
    void autoApprovedDispute_ShouldCreditBalanceAndRecordAudit() {
        for (BigDecimal amount : AMOUNTS) {
            BigDecimal startingBalance = new BigDecimal("10000.00");
            TransactionEntity txn = persistAutoApproveTransaction(startingBalance, amount);

            DisputeRequestDto request = new DisputeRequestDto(
                    txn.getTransactionId(), DisputeReason.INCORRECT_DATE, "Debit posted on the wrong date");

            DisputeResponseDto response = disputeService.submitDispute(customerId, request);

            // Decision route must be AUTO_APPROVE for the refund to apply.
            assertThat(response.decisionRoute())
                    .as("amount=%s should route AUTO_APPROVE (score 35)", amount)
                    .isEqualTo(DecisionRoute.AUTO_APPROVE);
            assertThat(response.status()).isEqualTo(DisputeStatus.AUTO_APPROVED);

            // Balance credited by exactly the transaction amount.
            AccountEntity reloaded = accountRepository.findById(account.getAccountId()).orElseThrow();
            assertThat(reloaded.getBalance())
                    .as("newBalance == startingBalance + amount for amount=%s", amount)
                    .usingComparator(BigDecimal::compareTo)
                    .isEqualTo(startingBalance.add(amount));

            // Audit fields recorded on the persisted dispute.
            DisputeEntity persisted = reloadDispute(response.disputeReference());
            assertThat(persisted.getRefundedAmount())
                    .usingComparator(BigDecimal::compareTo)
                    .isEqualTo(amount);
            assertThat(persisted.getRefundedAt()).isNotNull();
            assertThat(response.refundedAmount())
                    .usingComparator(BigDecimal::compareTo)
                    .isEqualTo(amount);

            // Reset state for the next representative amount within this transactional test.
            disputeRepository.deleteAll();
            transactionRepository.deleteAll();
        }
    }

    // ==========================================================================================
    // Property 8 (task 7.8) — Manual-investigation disputes are not refunded.
    //
    // For a MANUAL_INVESTIGATION dispute: account balance is unchanged and the persisted
    // dispute's refundedAmount and refundedAt are both null.
    // **Validates: Requirements 6.2, 8.5**
    // ==========================================================================================
    @Test
    @DisplayName("Property 8: manual-investigation disputes leave balance unchanged and audit fields null")
    void manualInvestigationDispute_ShouldNotRefund() {
        for (BigDecimal amount : AMOUNTS) {
            BigDecimal startingBalance = new BigDecimal("10000.00");
            TransactionEntity txn = persistManualInvestigationTransaction(startingBalance, amount);

            DisputeRequestDto request = new DisputeRequestDto(
                    txn.getTransactionId(), DisputeReason.UNAUTHORISED_MANDATE, "Unexpected debit order");

            DisputeResponseDto response = disputeService.submitDispute(customerId, request);

            assertThat(response.decisionRoute())
                    .as("amount=%s should route MANUAL_INVESTIGATION (score 70)", amount)
                    .isEqualTo(DecisionRoute.MANUAL_INVESTIGATION);
            assertThat(response.status()).isEqualTo(DisputeStatus.QUEUED_FOR_AGENT);

            // Balance unchanged — no credit for a non-auto-approved dispute.
            AccountEntity reloaded = accountRepository.findById(account.getAccountId()).orElseThrow();
            assertThat(reloaded.getBalance())
                    .as("balance unchanged for MANUAL_INVESTIGATION amount=%s", amount)
                    .usingComparator(BigDecimal::compareTo)
                    .isEqualTo(startingBalance);

            // Audit fields never set.
            DisputeEntity persisted = reloadDispute(response.disputeReference());
            assertThat(persisted.getRefundedAmount()).isNull();
            assertThat(persisted.getRefundedAt()).isNull();
            assertThat(response.refundedAmount()).isNull();

            disputeRepository.deleteAll();
            transactionRepository.deleteAll();
        }
    }

    // ==========================================================================================
    // Property 9 (task 7.9) — Refund idempotence.
    //
    // The refund must credit the balance exactly once; it must never double-credit.
    //
    // How idempotence is demonstrated here: applyRefundIfEligible is private and the only
    // public entry point, submitDispute, carries a duplicate-dispute guard (one dispute per
    // transaction). A genuine second refund attempt therefore flows through submitDispute,
    // which rejects the repeat with BadRequestException. We assert the guarded invariant:
    //   (1) the first submission credits the balance by exactly one amount and sets refundedAmount,
    //   (2) a second submitDispute for the SAME transaction throws BadRequestException (duplicate),
    //   (3) after that rejection the balance still equals startingBalance + amount (ONE credit,
    //       not two) and the dispute's refundedAmount is unchanged.
    // This proves the refund path cannot be driven to double-credit the same transaction.
    // **Validates: Requirements 7.1, 7.2, 7.3**
    // ==========================================================================================
    @Test
    @DisplayName("Property 9: refund is idempotent — the same transaction credits the balance exactly once")
    void refund_ShouldBeIdempotent_NoDoubleCredit() {
        for (BigDecimal amount : AMOUNTS) {
            BigDecimal startingBalance = new BigDecimal("10000.00");
            TransactionEntity txn = persistAutoApproveTransaction(startingBalance, amount);

            DisputeRequestDto request = new DisputeRequestDto(
                    txn.getTransactionId(), DisputeReason.INCORRECT_DATE, "Debit posted on the wrong date");

            // First submission: auto-approved, single credit applied.
            DisputeResponseDto first = disputeService.submitDispute(customerId, request);
            assertThat(first.decisionRoute()).isEqualTo(DecisionRoute.AUTO_APPROVE);

            BigDecimal expectedSingleCredit = startingBalance.add(amount);
            AccountEntity afterFirst = accountRepository.findById(account.getAccountId()).orElseThrow();
            assertThat(afterFirst.getBalance())
                    .usingComparator(BigDecimal::compareTo)
                    .isEqualTo(expectedSingleCredit);

            BigDecimal refundedAfterFirst = reloadDispute(first.disputeReference()).getRefundedAmount();
            assertThat(refundedAfterFirst).usingComparator(BigDecimal::compareTo).isEqualTo(amount);

            // Second submission for the SAME transaction is rejected by the duplicate guard,
            // so the refund path cannot run a second time.
            assertThatThrownBy(() -> disputeService.submitDispute(customerId, request))
                    .isInstanceOf(BadRequestException.class);

            // Balance still reflects exactly ONE credit — no double credit occurred.
            AccountEntity afterSecond = accountRepository.findById(account.getAccountId()).orElseThrow();
            assertThat(afterSecond.getBalance())
                    .as("balance stays at a single credit after the duplicate attempt, amount=%s", amount)
                    .usingComparator(BigDecimal::compareTo)
                    .isEqualTo(expectedSingleCredit);

            // Dispute audit amount unchanged.
            assertThat(reloadDispute(first.disputeReference()).getRefundedAmount())
                    .usingComparator(BigDecimal::compareTo)
                    .isEqualTo(amount);

            disputeRepository.deleteAll();
            transactionRepository.deleteAll();
        }
    }
}
