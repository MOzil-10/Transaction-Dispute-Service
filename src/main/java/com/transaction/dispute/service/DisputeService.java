package com.transaction.dispute.service;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.engine.DecisionMatrix;
import com.transaction.dispute.engine.RiskScoreEngine;
import com.transaction.dispute.exception.BadRequestException;
import com.transaction.dispute.exception.ResourceNotFoundException;
import com.transaction.dispute.exception.ForbiddenException;
import com.transaction.dispute.mapper.DisputeMapper;
import com.transaction.dispute.model.dto.DisputeRequestDto;
import com.transaction.dispute.model.dto.DisputeResponseDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.model.entity.DisputeEntity;
import com.transaction.dispute.model.entity.TransactionEntity;
import com.transaction.dispute.model.entity.UserEntity;
import com.transaction.dispute.model.enums.DecisionRoute;
import com.transaction.dispute.model.enums.DisputeStatus;
import com.transaction.dispute.repository.AccountRepository;
import com.transaction.dispute.repository.DisputeRepository;
import com.transaction.dispute.repository.TransactionRepository;
import com.transaction.dispute.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final DisputeMapper disputeMapper;
    private final RiskScoreEngine riskScoreEngine;
    private final DecisionMatrix decisionMatrix;
    private final SqsPublisherService sqsPublisherService;
    private final Clock clock;

    @Transactional
    public DisputeResponseDto submitDispute(Long customerId, DisputeRequestDto request) {
        log.info("Processing dispute submission — customerId={}, transactionId={}", customerId, request.transactionId());

        UserEntity customer = userRepository.findById(customerId)
                .orElseThrow(() -> {
                    log.error("Customer not found — customerId={}", customerId);
                    return new ResourceNotFoundException(AppConstants.MSG_USER_NOT_FOUND_ID + customerId);
                });

        TransactionEntity transaction = transactionRepository.findById(request.transactionId())
                .orElseThrow(() -> {
                    log.error("Transaction not found — transactionId={}", request.transactionId());
                    return new ResourceNotFoundException(AppConstants.MSG_TRANSACTION_NOT_FOUND + request.transactionId());
                });

        // Ownership check — transaction must belong to the authenticated customer
        if (!transaction.getAccount().getUser().getCustomerId().equals(customerId)) {
            log.error("Unauthorized dispute attempt — customerId={} does not own transactionId={}", customerId, request.transactionId());
            throw new ForbiddenException(AppConstants.MSG_UNAUTHORIZED_TRANSACTION_ACCESS);
        }

        // 60-day regulatory dispute window (PASA, effective 2026-04-13)
        assertWithinDisputeWindow(transaction);

        // Transaction-category eligibility
        assertCategoryDisputable(transaction);

        // Duplicate dispute guard
        if (disputeRepository.existsByTransactionTransactionId(request.transactionId())) {
            log.error("Duplicate dispute rejected — transactionId={}", request.transactionId());
            throw new BadRequestException(AppConstants.MSG_DISPUTE_ALREADY_EXISTS + request.transactionId());
        }

        // Risk scoring and decision routing
        int riskScore = riskScoreEngine.calculate(transaction, request.reason());
        DecisionRoute decisionRoute = decisionMatrix.evaluate(riskScore);
        DisputeStatus status = decisionRoute == DecisionRoute.AUTO_APPROVE
                ? DisputeStatus.AUTO_APPROVED
                : DisputeStatus.QUEUED_FOR_AGENT;

        log.debug("Dispute decision — transactionId={}, riskScore={}, route={}, status={}", request.transactionId(), riskScore, decisionRoute, status);

        // Persist dispute
        DisputeEntity dispute = DisputeEntity.builder()
                .disputeReference("DSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .transaction(transaction)
                .user(customer)
                .reason(request.reason())
                .description(request.description())
                .riskScore(riskScore)
                .decisionRoute(decisionRoute)
                .status(status)
                .build();

        DisputeEntity saved = disputeRepository.save(dispute);
        log.info("Dispute saved — disputeReference={}, status={}", saved.getDisputeReference(), saved.getStatus());

        // Apply auto-approve refund (atomic with dispute within this @Transactional method)
        applyRefundIfEligible(saved, transaction, decisionRoute);
        disputeRepository.save(saved);

        // Publish event to SQS — non-fatal, sqsMessageId stored for audit
        String sqsMessageId = sqsPublisherService.publishDisputeEvent(saved);
        if (sqsMessageId != null) {
            saved.setSqsMessageId(sqsMessageId);
            disputeRepository.save(saved);
            log.debug("SQS message ID persisted — disputeReference={}, sqsMessageId={}", saved.getDisputeReference(), sqsMessageId);
        }

        // Resolve user-facing message from decision
        String message = decisionRoute == DecisionRoute.AUTO_APPROVE
                ? AppConstants.MSG_DISPUTE_AUTO_APPROVED
                : AppConstants.MSG_DISPUTE_QUEUED_AGENT;

        DisputeResponseDto dto = disputeMapper.toDto(saved);
        return new DisputeResponseDto(
                dto.disputeId(),
                dto.disputeReference(),
                dto.transactionId(),
                dto.amount(),
                dto.reason(),
                dto.riskScore(),
                dto.decisionRoute(),
                dto.status(),
                saved.getRefundedAmount(),
                message,
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public Page<DisputeResponseDto> getDisputeHistory(Long customerId, Pageable pageable) {
        log.info("Fetching dispute history — customerId={}", customerId);

        if (!userRepository.existsById(customerId)) {
            log.error("Customer not found customerId={}", customerId);
            throw new ResourceNotFoundException(AppConstants.MSG_USER_NOT_FOUND_ID + customerId);
        }

        Page<DisputeResponseDto> page = disputeRepository
                .findByUserCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(entity -> {
                    DisputeResponseDto dto = disputeMapper.toDto(entity);
                    String message = entity.getDecisionRoute() == DecisionRoute.AUTO_APPROVE
                            ? AppConstants.MSG_DISPUTE_AUTO_APPROVED
                            : AppConstants.MSG_DISPUTE_QUEUED_AGENT;
                    return new DisputeResponseDto(
                            dto.disputeId(),
                            dto.disputeReference(),
                            dto.transactionId(),
                            dto.amount(),
                            dto.reason(),
                            dto.riskScore(),
                            dto.decisionRoute(),
                            dto.status(),
                            entity.getRefundedAmount(),
                            message,
                            entity.getCreatedAt()
                    );
                });

        log.debug("Found {} disputes for customerId={}", page.getTotalElements(), customerId);
        return page;
    }

    private void assertWithinDisputeWindow(TransactionEntity transaction) {
        ZoneId zone = ZoneId.of(AppConstants.DISPUTE_TIMEZONE);
        LocalDate txDate = transaction.getTransactionDate().atZoneSameInstant(zone).toLocalDate();
        LocalDate today = LocalDate.now(clock);
        long elapsed = ChronoUnit.DAYS.between(txDate, today);
        if (elapsed > AppConstants.DISPUTE_WINDOW_DAYS) {
            log.error("Dispute window closed — transactionId={}, elapsedDays={}", transaction.getTransactionId(), elapsed);
            throw new BadRequestException(AppConstants.MSG_DISPUTE_WINDOW_CLOSED);
        }
    }

    private void applyRefundIfEligible(DisputeEntity dispute, TransactionEntity transaction, DecisionRoute route) {
        // Idempotency: never credit twice (Req 7)
        if (route != DecisionRoute.AUTO_APPROVE || dispute.getRefundedAmount() != null) {
            return;
        }
        AccountEntity account = transaction.getAccount();
        account.setBalance(account.getBalance().add(transaction.getAmount()));
        accountRepository.save(account);
        dispute.setRefundedAmount(transaction.getAmount());
        dispute.setRefundedAt(OffsetDateTime.now(clock));
        log.info("Dispute auto-approved refund applied — disputeReference={}, refundedAmount={}", dispute.getDisputeReference(), dispute.getRefundedAmount());
    }

    private void assertCategoryDisputable(TransactionEntity transaction) {
        if (!transaction.getCategory().isDisputable()) {
            log.error("Non-disputable category rejected — transactionId={}, category={}", transaction.getTransactionId(), transaction.getCategory());
            throw new BadRequestException(AppConstants.MSG_CATEGORY_NOT_DISPUTABLE);
        }
    }
}

