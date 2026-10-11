#!/bin/bash
# LocalStack init hook — runs automatically once LocalStack is ready.
# Creates the FIFO queue used by the dispute portal for agent notifications.
set -e

echo "Creating SQS FIFO queue: dispute-events.fifo"
awslocal sqs create-queue \
  --queue-name dispute-events.fifo \
  --attributes FifoQueue=true,ContentBasedDeduplication=true

echo "SQS queue created successfully."
awslocal sqs list-queues
