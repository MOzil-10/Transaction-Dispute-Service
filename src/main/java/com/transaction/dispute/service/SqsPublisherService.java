package com.transaction.dispute.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transaction.dispute.model.entity.DisputeEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.Map;

/**
 * Publishes dispute event notifications to AWS SQS after decision routing.
 *
 * The message payload contains only non-PII identifiers (disputeReference,
 * transactionId, decisionRoute, status) sufficient for back-office processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SqsPublisherService {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.dispute-queue-url}")
    private String disputeQueueUrl;

    /**
     * Publishes a dispute event to SQS.
     *
     * @return the SQS message ID, or null if publishing failed (non-fatal)
     */
    public String publishDisputeEvent(DisputeEntity dispute) {
        log.info("Publishing dispute event to SQS — disputeReference={}, route={}", dispute.getDisputeReference(), dispute.getDecisionRoute());
        try {
            String messageBody = buildMessageBody(dispute);

            SendMessageResponse response = sqsClient.sendMessage(
                    SendMessageRequest.builder()
                            .queueUrl(disputeQueueUrl)
                            .messageBody(messageBody)
                            .messageGroupId(dispute.getDisputeReference())
                            .messageDeduplicationId(dispute.getDisputeReference())
                            .build()
            );

            log.info("SQS message sent — sqsMessageId={}, disputeReference={}", response.messageId(), dispute.getDisputeReference());
            return response.messageId();

        } catch (Exception ex) {
            // SQS failure is non-fatal — dispute is already persisted.
            // Log the error and allow the transaction to complete.
            log.error("Failed to publish dispute event to SQS — disputeReference={}: {}", dispute.getDisputeReference(), ex.getMessage());
            return null;
        }
    }

    private String buildMessageBody(DisputeEntity dispute) throws JsonProcessingException {
        Map<String, Object> payload = Map.of(
                "disputeReference", dispute.getDisputeReference(),
                "transactionId",    dispute.getTransaction().getTransactionId(),
                "reason",           dispute.getReason().name(),
                "riskScore",        dispute.getRiskScore(),
                "decisionRoute",    dispute.getDecisionRoute().name(),
                "status",           dispute.getStatus().name()
        );
        return objectMapper.writeValueAsString(payload);
    }
}

