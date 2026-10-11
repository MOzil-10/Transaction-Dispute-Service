package com.transaction.dispute.service;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.config.TestSqsConfig;
import com.transaction.dispute.exception.BadRequestException;
import com.transaction.dispute.exception.ResourceNotFoundException;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.model.dto.TransactionResponseDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.model.entity.TransactionEntity;
import com.transaction.dispute.model.enums.MandateType;
import com.transaction.dispute.repository.AccountRepository;
import com.transaction.dispute.repository.TransactionRepository;
import com.transaction.dispute.repository.UserRepository;
import com.transaction.dispute.util.TestPayloadFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for TransactionService.searchTransactions: unfiltered retrieval plus optional
 * mandate-type and inclusive SAST date-range filters, date-range validation, and the
 * account-not-found 404 behavior.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(TestSqsConfig.class)
class TransactionSearchServiceTest {

    @Autowired private TransactionService transactionService;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;

    private static final ZoneId SAST = ZoneId.of(AppConstants.DISPUTE_TIMEZONE);
    private static final PageRequest PAGE =
            PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "transactionDate"));

    private Long customerId;
    private AccountEntity account;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        RegisterRequestDto request = TestPayloadFactory.createRegisterRequest();
        customerId = authService.register(request).customerId();
        account = accountRepository.findByUserCustomerId(customerId).orElseThrow();

        // Three transactions on distinct SAST calendar days with mixed mandate types.
        save(MandateType.DEBICHECK,    LocalDate.of(2026, 3, 1));   // day A
        save(MandateType.STANDARD_EFT, LocalDate.of(2026, 3, 10));  // day B
        save(MandateType.STANDARD_EFT, LocalDate.of(2026, 3, 20));  // day C
    }

    private void save(MandateType mandate, LocalDate sastDate) {
        TransactionEntity t = TestPayloadFactory.createTransactionEntity(account, "TXN-" + UUID.randomUUID());
        t.setMandateType(mandate);
        t.setTransactionDate(sastDate.atTime(12, 0).atZone(SAST).toOffsetDateTime());
        transactionRepository.save(t);
    }

    @Test
    @DisplayName("No filters returns all of the customer's transactions")
    void search_ShouldReturnAll_WhenNoFilters() {
        Page<TransactionResponseDto> page = transactionService.searchTransactions(customerId, null, null, null, PAGE);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("Mandate-type filter narrows to matching transactions")
    void search_ShouldFilterByMandateType() {
        Page<TransactionResponseDto> debicheck = transactionService.searchTransactions(customerId, MandateType.DEBICHECK, null, null, PAGE);
        assertThat(debicheck.getTotalElements()).isEqualTo(1);
        assertThat(debicheck.getContent()).allMatch(t -> t.mandateType() == MandateType.DEBICHECK);

        Page<TransactionResponseDto> eft = transactionService.searchTransactions(customerId, MandateType.STANDARD_EFT, null, null, PAGE);
        assertThat(eft.getTotalElements()).isEqualTo(2);
        assertThat(eft.getContent()).allMatch(t -> t.mandateType() == MandateType.STANDARD_EFT);
    }

    @Test
    @DisplayName("Date range is inclusive on both ends (SAST day bounds)")
    void search_ShouldFilterByInclusiveDateRange() {
        // Range covering only day B (2026-03-10): inclusive bounds must include the midday txn.
        Page<TransactionResponseDto> onlyB = transactionService.searchTransactions(
                customerId, null, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10), PAGE);
        assertThat(onlyB.getTotalElements()).isEqualTo(1);

        // Range A..B inclusive -> 2 transactions (day A and day B), excludes day C.
        Page<TransactionResponseDto> aToB = transactionService.searchTransactions(
                customerId, null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10), PAGE);
        assertThat(aToB.getTotalElements()).isEqualTo(2);

        // Open-ended lower bound only (from day B) -> day B and day C.
        Page<TransactionResponseDto> fromB = transactionService.searchTransactions(
                customerId, null, LocalDate.of(2026, 3, 10), null, PAGE);
        assertThat(fromB.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("Mandate type and date range combine")
    void search_ShouldCombineMandateAndDate() {
        Page<TransactionResponseDto> page = transactionService.searchTransactions(
                customerId, MandateType.STANDARD_EFT, LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 31), PAGE);
        // Only day C (2026-03-20) is STANDARD_EFT within the range.
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("fromDate after toDate throws BadRequestException")
    void search_ShouldThrowBadRequest_WhenFromAfterTo() {
        assertThatThrownBy(() -> transactionService.searchTransactions(
                customerId, null, LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 1), PAGE))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(AppConstants.MSG_INVALID_DATE_RANGE);
    }

    @Test
    @DisplayName("Non-existent customer throws ResourceNotFoundException (404)")
    void search_ShouldThrowNotFound_WhenCustomerHasNoAccount() {
        Long missing = 8_888_888L;
        assertThatThrownBy(() -> transactionService.searchTransactions(missing, null, null, null, PAGE))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(AppConstants.MSG_ACCOUNT_NOT_FOUND + missing);
    }
}