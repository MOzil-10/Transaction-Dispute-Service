package com.transaction.dispute.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Replaces the real SqsClient with a no-op mock for all tests.
 *
 * Prevents any real AWS SDK calls during the H2 test suite while still
 * allowing DisputeService SQS integration code paths to execute.
 * Returns a fixed fake message ID so sqsMessageId persistence can be verified.
 */
@TestConfiguration
public class TestSqsConfig {

    @Bean
    @Primary
    public SqsClient sqsClient() {
        SqsClient mockClient = mock(SqsClient.class);
        when(mockClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder()
                        .messageId("test-sqs-message-id")
                        .build());
        return mockClient;
    }
}

