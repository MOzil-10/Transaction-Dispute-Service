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

    /**
     * Returns the given customer's transactions, optionally filtered by mandate type and/or an
     * inclusive transaction-date range. Any filter left null is ignored, so passing all nulls
     * returns every transaction for the customer.
     */
    @Query("select t from TransactionEntity t where t.account.user.customerId = :customerId " +
            "and (:mandateType is null or t.mandateType = :mandateType) " +
            "and (:from is null or t.transactionDate >= :from) " +
            "and (:to is null or t.transactionDate <= :to)")
    Page<TransactionEntity> search(@Param("customerId") Long customerId,
                                   @Param("mandateType") MandateType mandateType,
                                   @Param("from") OffsetDateTime from,
                                   @Param("to") OffsetDateTime to,
                                   Pageable pageable);
}