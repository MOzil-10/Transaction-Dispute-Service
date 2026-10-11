package com.transaction.dispute.service;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.config.TestSqsConfig;
import com.transaction.dispute.exception.BadRequestException;
import com.transaction.dispute.model.dto.DisputeRequestDto;
import com.transaction.dispute.model.dto.DisputeResponseDto;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.model.entity.TransactionEntity;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Day-60 / day-61 and timezone boundary tests for the 60-calendar-day dispute window,
 * driving {@link DisputeService#submitDispute} against H2 with a FIXED clock.
 *
 * <p>The production {@code Clock} bean (SAST-anchored system clock in {@code TimeConfig})
 * is overridden here by {@link FixedClockConfig} so the day-60 vs day-61 boundary is
 * deterministic — {@code allow-bean-definition-overriding: true} in application-test.yml
 * permits the replacement. The clock is pinned to the instant 2026-06-01T10:00:00Z, which
 * in Africa/Johannesburg (UTC+2) resolves to the SAST calendar date 2026-06-01. That fixed
 * SAST "today" is what makes these assertions reproducible: a transaction dated "now" by the
 * real system clock would trivially be in-window, but here every decision is measured against
 * the frozen 2026-06-01 reference date (Req 2.1, 2.2).
 *
 * <p>Window rule under test (Req 1): {@code elapsed = DAYS.between(txSastDate, 2026-06-01)};
 * accepted when {@code elapsed <= 60}, rejected when {@code elapsed > 60}. All transactions use
 * a DISPUTABLE category (GYM_MEMBERSHIP) and a disputable mandate type so ONLY the window rule
 * governs the accept/reject outcome.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import({TestSqsConfig.class, DisputeWindowBoundaryTest.FixedClockConfig.class})
class DisputeWindowBoundaryTest {

    // The SAST calendar date the fixed clock resolves to: 2026-06-01T10:00:00Z -> 12:00+02:00 -> 2026-06-01.
    private static final LocalDate CLOCK_SAST_DATE = LocalDate.of(2026, 6, 1);
    private static final ZoneId SAST = ZoneId.of(AppConstants.DISPUTE_TIMEZONE);
    private static final ZoneOffset SAST_OFFSET = ZoneOffset.ofHours(2);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneId.of("Africa/Johannesburg"));
        }
    }

    @Autowired private DisputeService disputeService;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private DisputeRepository disputeRepository;

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

    /**
     * Builds a disputable transaction (GYM_MEMBERSHIP, STANDARD_EFT) with the supplied
     * transactionDate so only the window rule is exercised.
     */
    private TransactionEntity saveTransaction(OffsetDateTime transactionDate) {
        TransactionEntity transaction = TransactionEntity.builder()
                .account(account)
                .reference("TXN-" + UUID.randomUUID())
                .merchant("FITNESS_CLUB_ZA")
                .amount(new BigDecimal("450.00"))
                .currency("ZAR")
                .mandateType(MandateType.STANDARD_EFT)
                .category(TransactionCategory.GYM_MEMBERSHIP)
                .status(TransactionStatus.COMPLETED)
                .transactionDate(transactionDate)
                .build();
        return transactionRepository.save(transaction);
    }

    // ---------------------------------------------------------------------------------------------
    // Req 1.3 — Day 60 is INSIDE the window (inclusive): elapsed == 60 -> accepted.
    // SAST date exactly 60 days before the fixed clock date (2026-06-01) -> 2026-04-02, midday +02:00.
    // ---------------------------------------------------------------------------------------------
    @Test
    @DisplayName("Day 60 — SAST date 60 days before fixed clock is within window and succeeds")
    void submitDispute_ShouldSucceed_WhenTransactionIsExactlyDay60() {
        LocalDate day60 = CLOCK_SAST_DATE.minusDays(60); // 2026-04-02
        OffsetDateTime transactionDate = day60.atTime(12, 0).atOffset(SAST_OFFSET);
        TransactionEntity transaction = saveTransaction(transactionDate);

        DisputeResponseDto response = disputeService.submitDispute(
                customerId, TestPayloadFactory.createDisputeRequest(transaction.getTransactionId()));

        assertThat(response).isNotNull();
        assertThat(response.disputeReference()).startsWith("DSP-");
        assertThat(response.decisionRoute()).isNotNull();
        assertThat(disputeRepository.count()).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------------------
    // Req 1.4 — Day 61 is OUTSIDE the window: elapsed == 61 -> rejected, no dispute persisted.
    // SAST date exactly 61 days before the fixed clock date (2026-06-01) -> 2026-04-01, midday +02:00.
    // ---------------------------------------------------------------------------------------------
    @Test
    @DisplayName("Day 61 — SAST date 61 days before fixed clock is outside window and rejected with no record")
    void submitDispute_ShouldRejectAndPersistNothing_WhenTransactionIsExactlyDay61() {
        LocalDate day61 = CLOCK_SAST_DATE.minusDays(61); // 2026-04-01
        OffsetDateTime transactionDate = day61.atTime(12, 0).atOffset(SAST_OFFSET);
        TransactionEntity transaction = saveTransaction(transactionDate);

        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(transaction.getTransactionId());

        assertThatThrownBy(() -> disputeService.submitDispute(customerId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(AppConstants.MSG_DISPUTE_WINDOW_CLOSED);

        // Req 9.6 — a window-rejected submission creates no DisputeEntity.
        assertThat(disputeRepository.count()).isZero();
    }

    // ---------------------------------------------------------------------------------------------
    // Req 1.5 — The SAST calendar day, not the UTC day, drives the elapsed-day count.
    //
    // Instant 2026-04-01T23:00:00Z is on calendar day 2026-04-01 in UTC but on 2026-04-02 in SAST
    // (UTC+2 -> 2026-04-02T01:00:00+02:00). The two interpretations flip the outcome:
    //   - As a UTC date (2026-04-01): elapsed = 61 -> would be REJECTED.
    //   - As a SAST date (2026-04-02): elapsed = 60 -> ACCEPTED.
    // The dispute SUCCEEDS, proving the service evaluates the window in Africa/Johannesburg.
    // ---------------------------------------------------------------------------------------------
    @Test
    @DisplayName("Zone correctness — instant that is day 61 in UTC but day 60 in SAST is accepted (SAST drives the count)")
    void submitDispute_ShouldUseSastDay_WhenUtcAndSastDatesDiffer() {
        OffsetDateTime utcCrossingInstant = OffsetDateTime.ofInstant(
                Instant.parse("2026-04-01T23:00:00Z"), ZoneOffset.UTC);

        // Sanity: this instant is a different calendar day in UTC vs SAST, and only the SAST
        // interpretation lands on day 60 (within window).
        assertThat(utcCrossingInstant.toLocalDate()).isEqualTo(LocalDate.of(2026, 4, 1));            // UTC day
        assertThat(utcCrossingInstant.atZoneSameInstant(SAST).toLocalDate())
                .isEqualTo(LocalDate.of(2026, 4, 2));                                                 // SAST day = day 60

        TransactionEntity transaction = saveTransaction(utcCrossingInstant);

        DisputeResponseDto response = disputeService.submitDispute(
                customerId, TestPayloadFactory.createDisputeRequest(transaction.getTransactionId()));

        assertThat(response).isNotNull();
        assertThat(response.disputeReference()).startsWith("DSP-");
        assertThat(disputeRepository.count()).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------------------
    // Req 2.1, 2.2 — The injected (fixed) clock, not the real system clock, governs the decision.
    //
    // transactionDate here is 2026-03-01 (SAST), which is 92 days before the frozen clock date
    // (2026-06-01) and therefore OUT of the 60-day window -> rejected. Under the REAL system
    // clock this same transaction would be far in the FUTURE (2026) and trivially in-window, so a
    // rejection here can only come from the service reading the injected fixed clock. This pins
    // Req 2.1/2.2: the window is evaluated against the supplied fixed instant.
    // ---------------------------------------------------------------------------------------------
    @Test
    @DisplayName("Clock injection — fixed clock (not system clock) drives rejection of an old transaction")
    void submitDispute_ShouldReadInjectedClock_NotSystemClock() {
        LocalDate farBeforeClock = CLOCK_SAST_DATE.minusDays(92); // 2026-03-01, well outside 60 days
        OffsetDateTime transactionDate = farBeforeClock.atTime(12, 0).atOffset(SAST_OFFSET);
        TransactionEntity transaction = saveTransaction(transactionDate);

        DisputeRequestDto request = TestPayloadFactory.createDisputeRequest(transaction.getTransactionId());

        assertThatThrownBy(() -> disputeService.submitDispute(customerId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(AppConstants.MSG_DISPUTE_WINDOW_CLOSED);

        assertThat(disputeRepository.count()).isZero();
    }
}
