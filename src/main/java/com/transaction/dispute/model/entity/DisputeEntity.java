package com.transaction.dispute.model.entity;

import com.transaction.dispute.model.enums.DecisionRoute;
import com.transaction.dispute.model.enums.DisputeReason;
import com.transaction.dispute.model.enums.DisputeStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "disputes")
public class DisputeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "dispute_seq_gen")
    @SequenceGenerator(name = "dispute_seq_gen", sequenceName = "dispute_seq", allocationSize = 1, initialValue = 3000000)
    @Column(name = "dispute_id", updatable = false, nullable = false)
    private Long disputeId;

    @Column(name = "dispute_reference", nullable = false, unique = true, length = 50)
    private String disputeReference;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private TransactionEntity transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeReason reason;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_route", nullable = false)
    private DecisionRoute decisionRoute;

    @Column(name = "sqs_message_id", length = 100)
    private String sqsMessageId;

    @Column(name = "refunded_amount", precision = 19, scale = 2)
    private BigDecimal refundedAmount;

    @Column(name = "refunded_at")
    private OffsetDateTime refundedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}