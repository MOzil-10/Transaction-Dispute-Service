package com.transaction.dispute.service;

import com.transaction.dispute.config.TestSqsConfig;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.repository.AccountRepository;
import com.transaction.dispute.repository.UserRepository;
import com.transaction.dispute.util.TestPayloadFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the @Version optimistic lock on AccountEntity. This guards the balance against lost
 * updates when two refunds credit the same account concurrently: the second (stale) write must be
 * rejected rather than silently overwriting the first.
 *
 * The class is intentionally NOT @Transactional so each save commits in its own transaction and
 * the version column is actually incremented in the database between reads.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestSqsConfig.class)
class AccountOptimisticLockingTest {

    @Autowired private AuthService authService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private UserRepository userRepository;

    private Long accountId;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
        RegisterRequestDto request = TestPayloadFactory.createRegisterRequest();
        Long customerId = authService.register(request).customerId();
        accountId = accountRepository.findByUserCustomerId(customerId).orElseThrow().getAccountId();
    }

    @AfterEach
    void tearDown() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("version is assigned on insert and increments on each committed update")
    void version_ShouldIncrementOnUpdate() {
        AccountEntity account = accountRepository.findById(accountId).orElseThrow();
        Long initialVersion = account.getVersion();
        assertThat(initialVersion).isNotNull();

        account.setBalance(account.getBalance().add(new BigDecimal("100.00")));
        accountRepository.saveAndFlush(account);

        Long afterVersion = accountRepository.findById(accountId).orElseThrow().getVersion();
        assertThat(afterVersion).isGreaterThan(initialVersion);
    }

    @Test
    @DisplayName("a stale write (older version) is rejected with an optimistic-locking failure")
    void staleWrite_ShouldBeRejected() {
        // Two in-memory copies loaded at the same version — simulating two concurrent refund flows.
        AccountEntity first = accountRepository.findById(accountId).orElseThrow();
        AccountEntity stale = accountRepository.findById(accountId).orElseThrow();

        // First writer commits, bumping the DB version.
        first.setBalance(first.getBalance().add(new BigDecimal("50.00")));
        accountRepository.saveAndFlush(first);

        // Second writer still holds the old version → must be rejected, not silently applied.
        stale.setBalance(stale.getBalance().add(new BigDecimal("999.00")));
        assertThatThrownBy(() -> accountRepository.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}