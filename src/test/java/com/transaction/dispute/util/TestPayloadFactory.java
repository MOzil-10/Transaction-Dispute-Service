package com.transaction.dispute.util;

import com.transaction.dispute.common.AppConstants;
import com.transaction.dispute.model.dto.AuthResponseDto;
import com.transaction.dispute.model.dto.LoginRequestDto;
import com.transaction.dispute.model.dto.RegisterRequestDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.model.entity.DisputeEntity;
import com.transaction.dispute.model.entity.TransactionEntity;
import com.transaction.dispute.model.entity.UserEntity;
import com.transaction.dispute.model.enums.DecisionRoute;
import com.transaction.dispute.model.enums.DisputeReason;
import com.transaction.dispute.model.enums.DisputeStatus;
import com.transaction.dispute.model.enums.MandateType;
import com.transaction.dispute.model.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class TestPayloadFactory {

    private TestPayloadFactory() {}

    public static RegisterRequestDto createRegisterRequest() {
        return new RegisterRequestDto("Rita", "Sithole", "rita.sithole@example.co.za", "Password123!");
    }

    public static LoginRequestDto createLoginRequest() {
        return new LoginRequestDto("rita.sithole@example.co.za", "Password123!");
    }

    public static AuthResponseDto createAuthResponse() {
        return new AuthResponseDto("mocked.jwt.bearer.token", 5000001L, "rita.sithole@example.co.za");
    }

    public static UserEntity createUserEntity() {
        return UserEntity.builder()
                .customerId(5000001L)
                .customerNumber("CUST-100200300")
                .firstName("Rita")
                .lastName("Sithole")
                .email("rita.sithole@example.co.za")
                .passwordHash("$2a$10$e8p1O3v0/N.9x.x.mockedhash")
                .build();
    }

    public static AccountEntity createAccountEntity(UserEntity user) {
        return AccountEntity.builder()
                .accountId(5000002L)
                .user(user)
                .accountNumber("1002938481")
                .accountType(AppConstants.DEFAULT_ACCOUNT_TYPE)
                .balance(new BigDecimal("10000.00"))
                .currency(AppConstants.DEFAULT_CURRENCY)
                .build();
    }

    public static TransactionEntity createTransactionEntity(AccountEntity account) {
        return TransactionEntity.builder()
                .transactionId(5000102L)
                .account(account)
                .reference("TXN-90812")
                .merchant("FITNESS_CLUB_ZA")
                .amount(new BigDecimal("450.00"))
                .currency("ZAR")
                .mandateType(MandateType.STANDARD_EFT)
                .status(TransactionStatus.COMPLETED)
                .transactionDate(OffsetDateTime.now())
                .build();
    }

    public static DisputeEntity createDisputeEntity(UserEntity user, TransactionEntity transaction) {
        return DisputeEntity.builder()
                .disputeId(5000401L)
                .disputeReference("DSP-77491")
                .user(user)
                .transaction(transaction)
                .reason(DisputeReason.UNAUTHORISED_MANDATE)
                .description("Unauthorised debit order")
                .riskScore(75)
                .decisionRoute(DecisionRoute.MANUAL_INVESTIGATION)
                .status(DisputeStatus.QUEUED_FOR_AGENT)
                .build();
    }
}