package com.transaction.dispute.mapper;

import com.transaction.dispute.model.dto.DisputeResponseDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.model.entity.DisputeEntity;
import com.transaction.dispute.model.entity.TransactionEntity;
import com.transaction.dispute.model.entity.UserEntity;
import com.transaction.dispute.util.TestPayloadFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class DisputeMapperTest {

    private final DisputeMapper disputeMapper = Mappers.getMapper(DisputeMapper.class);

    // This test verifies mapping from DisputeEntity to DisputeResponseDto including nested transaction properties
    @Test
    @DisplayName("Should correctly map DisputeEntity to DisputeResponseDto with nested transaction values")
    void toDto_ShouldMapEntityToDto() {
        // Given
        UserEntity user = TestPayloadFactory.createUserEntity();
        AccountEntity account = TestPayloadFactory.createAccountEntity(user);
        TransactionEntity transaction = TestPayloadFactory.createTransactionEntity(account);
        DisputeEntity entity = TestPayloadFactory.createDisputeEntity(user, transaction);

        // When
        DisputeResponseDto dto = disputeMapper.toDto(entity);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.disputeId()).isEqualTo(entity.getDisputeId());
        assertThat(dto.disputeReference()).isEqualTo(entity.getDisputeReference());
        assertThat(dto.transactionId()).isEqualTo(transaction.getTransactionId());
        assertThat(dto.amount()).isEqualTo(transaction.getAmount());
        assertThat(dto.reason()).isEqualTo(entity.getReason());
        assertThat(dto.riskScore()).isEqualTo(entity.getRiskScore());
        assertThat(dto.decisionRoute()).isEqualTo(entity.getDecisionRoute());
        assertThat(dto.status()).isEqualTo(entity.getStatus());
        assertThat(dto.message()).isNull(); // Mapped dynamically in service layer
    }

    // This test verifies null safety when passing a null entity to toDto
    @Test
    @DisplayName("Should return null when mapping null DisputeEntity")
    void toDto_ShouldReturnNull_WhenEntityIsNull() {
        assertThat(disputeMapper.toDto(null)).isNull();
    }

    // This test verifies proper mapping when the nested transaction reference in DisputeEntity is null
    @Test
    @DisplayName("Should handle null transaction inside DisputeEntity gracefully")
    void toDto_ShouldHandleNullTransaction_Gracefully() {
        // Given
        UserEntity user = TestPayloadFactory.createUserEntity();
        DisputeEntity entity = TestPayloadFactory.createDisputeEntity(user, null);

        // When
        DisputeResponseDto dto = disputeMapper.toDto(entity);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.disputeId()).isEqualTo(entity.getDisputeId());
        assertThat(dto.transactionId()).isNull();
        assertThat(dto.amount()).isNull();
    }
}