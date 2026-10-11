package com.transaction.dispute.service;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.exception.ResourceNotFoundException;
import com.transaction.dispute.model.dto.AccountResponseDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.model.entity.UserEntity;
import com.transaction.dispute.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public AccountResponseDto getAccountOverview(Long customerId) {
        log.info("Fetching account overview for customerId={}", customerId);

        AccountEntity account = accountRepository.findByUserCustomerId(customerId)
                .orElseThrow(() -> {
                    log.error("Account not found for customerId={}", customerId);
                    return new ResourceNotFoundException(AppConstants.MSG_ACCOUNT_NOT_FOUND + customerId);
                });

        UserEntity user = account.getUser();

        return new AccountResponseDto(
                user.getCustomerId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getBalance(),
                account.getCurrency()
        );
    }
}