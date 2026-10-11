package com.transaction.dispute.service;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.exception.ResourceNotFoundException;
import com.transaction.dispute.model.dto.AccountResponseDto;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.repository.AccountRepository;
import com.transaction.dispute.repository.TransactionRepository;
import com.transaction.dispute.repository.UserRepository;
import com.transaction.dispute.util.TestPayloadFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@org.springframework.context.annotation.Import(com.transaction.dispute.config.TestSqsConfig.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

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

    // Verifies the overview maps user profile and account fields for an existing customer
    @Test
    @DisplayName("Should return account overview with customer and account details")
    void getAccountOverview_ShouldReturnOverview_WhenCustomerExists() {
        AccountResponseDto result = accountService.getAccountOverview(customerId);

        assertThat(result).isNotNull();
        assertThat(result.customerId()).isEqualTo(customerId);
        assertThat(result.firstName()).isEqualTo(account.getUser().getFirstName());
        assertThat(result.lastName()).isEqualTo(account.getUser().getLastName());
        assertThat(result.email()).isEqualTo(account.getUser().getEmail());
        assertThat(result.accountNumber()).isEqualTo(account.getAccountNumber());
        assertThat(result.accountType()).isEqualTo(account.getAccountType());
        assertThat(result.balance()).isEqualByComparingTo(account.getBalance());
        assertThat(result.currency()).isEqualTo(account.getCurrency());
    }

    // Verifies ResourceNotFoundException is thrown when no account exists for the given customerId
    @Test
    @DisplayName("Should throw ResourceNotFoundException when customer has no account")
    void getAccountOverview_ShouldThrowResourceNotFoundException_WhenCustomerHasNoAccount() {
        assertThatThrownBy(() -> accountService.getAccountOverview(99999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(AppConstants.MSG_ACCOUNT_NOT_FOUND);
    }
}