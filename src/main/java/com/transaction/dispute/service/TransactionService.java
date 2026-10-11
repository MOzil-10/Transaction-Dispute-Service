package com.transaction.dispute.service;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.exception.BadRequestException;
import com.transaction.dispute.exception.ResourceNotFoundException;
import com.transaction.dispute.mapper.TransactionMapper;
import com.transaction.dispute.model.dto.TransactionResponseDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.model.enums.MandateType;
import com.transaction.dispute.repository.AccountRepository;
import com.transaction.dispute.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

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

    /**
     * Returns the customer's transactions, optionally filtered by mandate type and/or an inclusive
     * calendar-date range. Dates are interpreted in the SAST (Africa/Johannesburg) timezone:
     * fromDate maps to start-of-day, toDate maps to end-of-day. Any filter left null is ignored, so
     * calling with all nulls returns every transaction (same result set as getTransactionPage).
     */
    @Transactional(readOnly = true)
    public Page<TransactionResponseDto> searchTransactions(Long customerId,
                                                           MandateType mandateType,
                                                           LocalDate fromDate,
                                                           LocalDate toDate,
                                                           Pageable pageable) {
        log.info("Searching transactions for customerId={}, mandateType={}, fromDate={}, toDate={}", customerId, mandateType, fromDate, toDate);

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            log.error("Invalid date range for customerId={} — fromDate={} is after toDate={}", customerId, fromDate, toDate);
            throw new BadRequestException(AppConstants.MSG_INVALID_DATE_RANGE);
        }

        // Preserve the account-not-found 404 behavior consistent with getTransactionPage.
        AccountEntity account = accountRepository.findByUserCustomerId(customerId)
                .orElseThrow(() -> {
                    log.error("Account not found for - customerId={}", customerId);
                    return new ResourceNotFoundException(AppConstants.MSG_ACCOUNT_NOT_FOUND + customerId);
                });

        ZoneId zone = ZoneId.of(AppConstants.DISPUTE_TIMEZONE);
        OffsetDateTime from = fromDate == null ? null
                : fromDate.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime to = toDate == null ? null
                : toDate.atTime(LocalTime.MAX).atZone(zone).toOffsetDateTime();

        Page<TransactionResponseDto> page = transactionRepository
                .search(account.getUser().getCustomerId(), mandateType, from, to, pageable)
                .map(transactionMapper::toDto);

        log.debug("Search returned {} transactions for customerId={}", page.getTotalElements(), customerId);
        return page;
    }
}