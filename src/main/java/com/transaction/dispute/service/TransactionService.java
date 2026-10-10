package com.transaction.dispute.service;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.exception.ResourceNotFoundException;
import com.transaction.dispute.mapper.TransactionMapper;
import com.transaction.dispute.model.dto.TransactionResponseDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.repository.AccountRepository;
import com.transaction.dispute.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionMapper transactionMapper;

    @Transactional(readOnly = true)
    public Page<TransactionResponseDto> getTransactionPage(Long customerId, Pageable pageable) {
        log.info("Fetching transactions for customerId={}", customerId);

        AccountEntity account = accountRepository.findByUserCustomerId(customerId)
                .orElseThrow(() -> {
                    log.error("Account not found for customerId={}", customerId);
                    return new ResourceNotFoundException(AppConstants.MSG_ACCOUNT_NOT_FOUND + customerId);
                });

        Page<TransactionResponseDto> page = transactionRepository
                .findByAccountUserCustomerId(account.getUser().getCustomerId(), pageable)
                .map(transactionMapper::toDto);

        log.debug("Found {} transactions for customerId={}", page.getTotalElements(), customerId);
        return page;
    }
}

