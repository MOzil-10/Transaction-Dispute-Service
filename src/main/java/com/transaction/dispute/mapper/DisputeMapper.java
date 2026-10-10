package com.transaction.dispute.mapper;

import com.transaction.dispute.model.dto.DisputeResponseDto;
import com.transaction.dispute.model.entity.DisputeEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DisputeMapper {

    @Mapping(source = "transaction.transactionId", target = "transactionId")
    @Mapping(source = "transaction.amount", target = "amount")
    @Mapping(target = "message", ignore = true)
    DisputeResponseDto toDto(DisputeEntity entity);
}