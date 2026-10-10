package com.transaction.dispute.service;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.exception.ResourceNotFoundException;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.model.dto.TransactionResponseDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.model.entity.TransactionEntity;
import com.transaction.dispute.repository.AccountRepository;
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
class TransactionServiceTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Long customerId;
    private AccountEntity account;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        RegisterRequestDto request = TestPayloadFactory.createRegisterRequest();
        var authResponse = authService.register(request);
        customerId = authResponse.customerId();
        account = accountRepository.findByUserCustomerId(customerId).orElseThrow();
    }

    // Verifies paginated transaction fetch returns all saved transactions for the customer
    @Test
    @DisplayName("Should return paged transactions when customer has transactions")
    void getTransactionPage_ShouldReturnPagedTransactions_WhenCustomerHasTransactions() {
        // Given — two transactions with unique references
        TransactionEntity tx1 = TestPayloadFactory.createTransactionEntity(account, "TXN-" + UUID.randomUUID());
        TransactionEntity tx2 = TestPayloadFactory.createTransactionEntity(account, "TXN-" + UUID.randomUUID());
        transactionRepository.save(tx1);
        transactionRepository.save(tx2);

        // When
        Page<TransactionResponseDto> result = transactionService.getTransactionPage(
                customerId, PageRequest.of(0, 10));

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent().getFirst().transactionId()).isNotNull();
        assertThat(result.getContent().getFirst().reference()).isNotBlank();
        assertThat(result.getContent().getFirst().amount()).isPositive();
    }

    // Verifies an empty page is returned when no transactions exist for the customer
    @Test
    @DisplayName("Should return empty page when customer has no transactions")
    void getTransactionPage_ShouldReturnEmptyPage_WhenNoTransactionsExist() {
        // When — no transactions saved
        Page<TransactionResponseDto> result = transactionService.getTransactionPage(
                customerId, PageRequest.of(0, 10));

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isZero();
    }

    // Verifies ResourceNotFoundException is thrown when no account exists for the given customerId
    @Test
    @DisplayName("Should throw ResourceNotFoundException when customer has no account")
    void getTransactionPage_ShouldThrowResourceNotFoundException_WhenCustomerHasNoAccount() {
        // When / Then
        assertThatThrownBy(() ->
                transactionService.getTransactionPage(99999L, PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(AppConstants.MSG_ACCOUNT_NOT_FOUND);
    }
}

