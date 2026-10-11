package com.transaction.dispute.service;

import com.transaction.dispute.config.TestSqsConfig;
import com.transaction.dispute.model.dto.DisputeRequestDto;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.model.entity.TransactionEntity;
import com.transaction.dispute.model.enums.DisputeReason;
import com.transaction.dispute.model.enums.MandateType;
import com.transaction.dispute.model.enums.TransactionCategory;
import com.transaction.dispute.model.enums.TransactionStatus;
import com.transaction.dispute.repository.AccountRepository;
import com.transaction.dispute.repository.DisputeRepository;
import com.transaction.dispute.repository.TransactionRepository;
import com.transaction.dispute.repository.UserRepository;
import com.transaction.dispute.util.TestPayloadFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

// Feature: regulatory-dispute-eligibility — refund atomicity/rollback, Validates Requirements 6.3, 6.4.
//
// Goal: prove the submitDispute @Transactional unit is atomic. If the account-balance
// credit (accountRepository.save) fails mid-flight during an AUTO_APPROVE dispute, the
// ENTIRE transaction must roll back: no DisputeEntity row persists and the account balance
// is left exactly as it was.
//
// Design choices (documented per task guidance):
//  1. This class is deliberately NOT @Transactional. A test-managed transaction would wrap
//     the whole method in one rollback-at-the-end transaction and share the persistence
//     context with the service call, which both masks the real production rollback and makes
//     "freshly queried" assertions read stale first-level-cache state. By running without a
//     test transaction, submitDispute opens and commits/rolls back its OWN transaction, so we
//     observe the genuine rollback via brand-new queries after the exception. Manual cleanup
//     is done in @AfterEach (deleteAll).
//  2. @SpyBean AccountRepository (not @MockBean): registration during setUp() relies on the
//     real accountRepository.save to create the customer's account, so we keep real behaviour
//     everywhere and only stub the ONE save that performs the refund credit — set up with
//     doThrow immediately before the submitDispute call, after setup is complete.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestSqsConfig.class)
class DisputeRefundRollbackTest {

    @Autowired private DisputeService disputeService;
    @Autowired private AuthService authService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private DisputeRepository disputeRepository;
    @Autowired private UserRepository userRepository;

    // Spy so real reads/writes work; only the refund-credit save is stubbed to throw.
    @SpyBean private AccountRepository accountRepository;

    private Long customerId;
    private Long accountId;

    @BeforeEach
    void setUp() {
        // Fresh slate. Not relying on test-managed rollback (class is non-transactional).
        disputeRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        // Real registration path — exercises accountRepository.save for real (unstubbed yet).
        RegisterRequestDto request = TestPayloadFactory.createRegisterRequest();
        customerId = authService.register(request).customerId();
        AccountEntity account = accountRepository.findByUserCustomerId(customerId).orElseThrow();
        accountId = account.getAccountId();
    }

    @AfterEach
    void tearDown() {
        // Clear any stubbing so cleanup saves/deletes behave normally.
        Mockito.reset(accountRepository);
        disputeRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Rollback: a failed refund credit rolls back the whole dispute — no dispute row, balance unchanged")
    void failedRefundCredit_ShouldRollBackEntireDispute() {
        BigDecimal startingBalance = new BigDecimal("10000.00");

        // AUTO_APPROVE-eligible transaction: DEBICHECK (base 30) + INCORRECT_DATE (+5) = 35 (<= 50),
        // disputable category GYM_MEMBERSHIP, dated now() so window + category guards pass.
        AccountEntity account = accountRepository.findById(accountId).orElseThrow();
        account.setBalance(startingBalance);
        accountRepository.save(account);

        TransactionEntity txn = transactionRepository.save(TransactionEntity.builder()
                .account(account)
                .reference("TXN-" + UUID.randomUUID())
                .merchant("FITNESS_CLUB_ZA")
                .amount(new BigDecimal("450.00"))
                .currency("ZAR")
                .mandateType(MandateType.DEBICHECK)
                .category(TransactionCategory.GYM_MEMBERSHIP)
                .status(TransactionStatus.COMPLETED)
                .transactionDate(OffsetDateTime.now())
                .build());

        // Snapshot pre-state via fresh queries.
        long disputeCountBefore = disputeRepository.count();
        BigDecimal balanceBefore = accountRepository.findById(accountId).orElseThrow().getBalance();
        assertThat(balanceBefore).usingComparator(BigDecimal::compareTo).isEqualTo(startingBalance);

        DisputeRequestDto request = new DisputeRequestDto(
                txn.getTransactionId(), DisputeReason.INCORRECT_DATE, "Debit posted on the wrong date");

        // Force the refund credit to fail. Setup is complete, so stubbing save now only hits
        // the in-flight refund credit inside submitDispute.
        Mockito.doThrow(new RuntimeException("DB down"))
                .when(accountRepository).save(any(AccountEntity.class));

        // The whole transaction must fail.
        assertThatThrownBy(() -> disputeService.submitDispute(customerId, request))
                .isInstanceOf(RuntimeException.class);

        // Un-stub so verification queries work against real state.
        Mockito.reset(accountRepository);

        // Atomicity proof via fresh queries after the service transaction has rolled back:
        //  (1) no DisputeEntity was persisted,
        assertThat(disputeRepository.count())
                .as("no dispute row should persist after a rolled-back refund (Req 6.3)")
                .isEqualTo(disputeCountBefore);

        //  (2) the account balance is unchanged — the partial credit was undone (Req 6.4).
        BigDecimal balanceAfter = accountRepository.findById(accountId).orElseThrow().getBalance();
        assertThat(balanceAfter)
                .as("account balance must be unchanged after the dispute rolled back (Req 6.4)")
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(startingBalance);
    }
}
