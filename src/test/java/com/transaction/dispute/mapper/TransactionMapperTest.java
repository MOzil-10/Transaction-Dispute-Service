package com.transaction.dispute.mapper;

import com.transaction.dispute.model.dto.TransactionResponseDto;
import com.transaction.dispute.model.entity.AccountEntity;
import com.transaction.dispute.model.entity.TransactionEntity;
import com.transaction.dispute.model.entity.UserEntity;
import com.transaction.dispute.model.enums.TransactionCategory;
import com.transaction.dispute.util.TestPayloadFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionMapperTest {

    private final TransactionMapper transactionMapper = Mappers.getMapper(TransactionMapper.class);

    // This test verifies mapping from a single TransactionEntity to a TransactionResponseDto record
    @Test
    @DisplayName("Should correctly map TransactionEntity to TransactionResponseDto")
    void toDto_ShouldMapEntityToDto() {
        // Given
        UserEntity user = TestPayloadFactory.createUserEntity();
        AccountEntity account = TestPayloadFactory.createAccountEntity(user);
        TransactionEntity entity = TestPayloadFactory.createTransactionEntity(account);

        // When
        TransactionResponseDto dto = transactionMapper.toDto(entity);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.transactionId()).isEqualTo(entity.getTransactionId());
        assertThat(dto.reference()).isEqualTo(entity.getReference());
        assertThat(dto.merchant()).isEqualTo(entity.getMerchant());
        assertThat(dto.amount()).isEqualTo(entity.getAmount());
        assertThat(dto.currency()).isEqualTo(entity.getCurrency());
        assertThat(dto.mandateType()).isEqualTo(entity.getMandateType());
        assertThat(dto.status()).isEqualTo(entity.getStatus());
        assertThat(dto.transactionDate()).isEqualTo(entity.getTransactionDate());
    }

    // This test verifies null safety when passing a null entity to toDto
    @Test
    @DisplayName("Should return null when mapping null TransactionEntity")
    void toDto_ShouldReturnNull_WhenEntityIsNull() {
        assertThat(transactionMapper.toDto(null)).isNull();
    }

    // This test verifies mapping a list of TransactionEntity objects to a list of TransactionResponseDto records
    @Test
    @DisplayName("Should correctly map a list of TransactionEntities to Dto list")
    void toDtoList_ShouldMapEntityListToDtoList() {
        // Given
        UserEntity user = TestPayloadFactory.createUserEntity();
        AccountEntity account = TestPayloadFactory.createAccountEntity(user);
        TransactionEntity entity = TestPayloadFactory.createTransactionEntity(account);

        // When
        List<TransactionResponseDto> dtoList = transactionMapper.toDtoList(List.of(entity));

        // Then
        assertThat(dtoList).isNotNull().hasSize(1);
        assertThat(dtoList.getFirst().transactionId()).isEqualTo(entity.getTransactionId());
    }

    // This test verifies null safety when passing a null list to toDtoList
    @Test
    @DisplayName("Should return null when mapping null TransactionEntity list")
    void toDtoList_ShouldReturnNull_WhenListIsNull() {
        assertThat(transactionMapper.toDtoList(null)).isNull();
    }

    // Feature: regulatory-dispute-eligibility, Validates Requirements 5.1, 5.2, 5.3.
    // This test verifies a disputable category maps to disputable() == true
    @Test
    @DisplayName("Should map disputable category to disputable=true")
    void toDto_ShouldSetDisputableTrue_WhenCategoryIsDisputable() {
        // Given
        UserEntity user = TestPayloadFactory.createUserEntity();
        AccountEntity account = TestPayloadFactory.createAccountEntity(user);
        TransactionEntity entity = TestPayloadFactory.createTransactionEntity(account);
        entity.setCategory(TransactionCategory.LOAN);

        // When
        TransactionResponseDto dto = transactionMapper.toDto(entity);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.disputable()).isTrue();
    }

    // Feature: regulatory-dispute-eligibility, Validates Requirements 5.1, 5.2, 5.3.
    // This test verifies a non-disputable category maps to disputable() == false
    @Test
    @DisplayName("Should map non-disputable category to disputable=false")
    void toDto_ShouldSetDisputableFalse_WhenCategoryIsNotDisputable() {
        // Given
        UserEntity user = TestPayloadFactory.createUserEntity();
        AccountEntity account = TestPayloadFactory.createAccountEntity(user);
        TransactionEntity entity = TestPayloadFactory.createTransactionEntity(account);
        entity.setCategory(TransactionCategory.P2P_TRANSFER);

        // When
        TransactionResponseDto dto = transactionMapper.toDto(entity);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.disputable()).isFalse();
    }
}