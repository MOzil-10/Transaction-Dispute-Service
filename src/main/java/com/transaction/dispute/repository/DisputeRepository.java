package com.transaction.dispute.repository;

import com.transaction.dispute.model.entity.DisputeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DisputeRepository extends JpaRepository<DisputeEntity, Long> {

    boolean existsByTransactionTransactionId(Long transactionId);

    Page<DisputeEntity> findByUserCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);
}

