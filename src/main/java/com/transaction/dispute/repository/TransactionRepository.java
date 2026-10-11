package com.transaction.dispute.repository;

import com.transaction.dispute.model.entity.TransactionEntity;
import com.transaction.dispute.model.enums.MandateType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    Page<TransactionEntity> findByAccountUserCustomerId(Long customerId, Pageable pageable);

    @Query("select t from TransactionEntity t where t.account.user.customerId = :customerId " +
            "and (cast(:mandateType as string) is null or t.mandateType = :mandateType) " +
            "and (cast(:from as timestamp) is null or t.transactionDate >= :from) " +
            "and (cast(:to as timestamp) is null or t.transactionDate <= :to)")
    Page<TransactionEntity> search(@Param("customerId") Long customerId,
                                   @Param("mandateType") MandateType mandateType,
                                   @Param("from") OffsetDateTime from,
                                   @Param("to") OffsetDateTime to,
                                   Pageable pageable);
}