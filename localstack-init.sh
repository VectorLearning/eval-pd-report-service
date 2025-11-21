#!/bin/bash

echo "Initializing LocalStack resources..."

# Wait for LocalStack to be ready
sleep 5

# Create S3 bucket for reports
echo "Creating S3 bucket: ev-plus-reports-local"
awslocal s3 mb s3://ev-plus-reports-local
awslocal s3 ls

# Create SQS queue for async processing
echo "Creating SQS queue: ev-plus-reporting-local-queue"
awslocal sqs create-queue --queue-name ev-plus-reporting-local-queue

# List queues to verify
awslocal sqs list-queues

# Verify SES (no specific setup needed, just test endpoint)
echo "SES endpoint available at: http://localhost:4566"

echo "LocalStack initialization complete!"
