package com.transaction.dispute.mapper;

import com.transaction.dispute.model.dto.TransactionResponseDto;
import com.transaction.dispute.model.entity.TransactionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TransactionMapper {

    @Mapping(target = "disputable", expression = "java(entity.getCategory().isDisputable())")
    TransactionResponseDto toDto(TransactionEntity entity);

    List<TransactionResponseDto> toDtoList(List<TransactionEntity> entities);
}