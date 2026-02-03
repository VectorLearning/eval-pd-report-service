# LocalStack Setup for Local Development

## Overview

LocalStack provides local AWS service emulation for S3, SQS, and SES without requiring actual AWS credentials or incurring costs.

## Prerequisites

- Docker installed and running
- AWS CLI installed (for `awslocal` command)

## Installation

### 1. Install LocalStack AWS CLI Wrapper

```bash
pip install awscli-local
```

This installs `awslocal` command which is a wrapper around `aws` CLI that automatically points to LocalStack.

### 2. Start LocalStack

From the project root directory:

```bash
docker-compose -f docker-compose-localstack.yml up -d
```

This starts LocalStack container and automatically runs the initialization script that creates:
- S3 bucket: `ev-plus-reports-local`
- SQS queue: `ev-plus-reporting-local-queue`
- SES endpoint (no specific resources needed)

### 3. Verify LocalStack is Running

```bash
docker ps | grep localstack
```

You should see the container running on port 4566.

### 4. Verify Resources Created

```bash
# List S3 buckets
awslocal s3 ls

# List SQS queues
awslocal sqs list-queues

# Check LocalStack health
curl http://localhost:4566/_localstack/health
```

## Using LocalStack with the Application

When running the application with `local` profile:

```bash
mvn spring-boot:run -Dspring.profiles.active=local
```

The application will automatically connect to LocalStack services:
- S3: http://localhost:4566
- SQS: http://localhost:4566
- SES: http://localhost:4566

All S3 uploads, SQS messages, and email sends will go to LocalStack instead of real AWS.

## Testing Async Flow Locally

1. Start LocalStack:
   ```bash
   docker-compose -f docker-compose-localstack.yml up -d
   ```

2. Start the application:
   ```bash
   mvn spring-boot:run -Dspring.profiles.active=local
   ```

3. Generate an async report (large dataset):
   ```bash
   curl -X POST http://localhost:8080/vector-eval/v1/eval-pd-report/reports/generate \
     -H "Content-Type: application/json" \
     -d '{
       "reportType": "USER_ACTIVITY",
       "criteria": {
         "startDate": "2024-01-01",
         "endDate": "2024-12-31"
       }
     }'
   ```

4. Check SQS message was sent:
   ```bash
   awslocal sqs receive-message --queue-url http://localhost:4566/000000000000/ev-plus-reporting-local-queue
   ```

5. After async processing completes, check S3:
   ```bash
   awslocal s3 ls s3://ev-plus-reports-local/reports/ --recursive
   ```

6. Download report from S3:
   ```bash
   awslocal s3 cp s3://ev-plus-reports-local/reports/path/to/report.xlsx ./report.xlsx
   ```

## Email Testing

Emails sent via SES will be logged to LocalStack console instead of actually sent.

To view email logs:

```bash
docker logs evplus-localstack | grep -A 20 "SendEmail"
```

Or use LocalStack's built-in dashboard (Pro version only):
```
http://localhost:4566/_localstack/dashboard
```

## Stopping LocalStack

```bash
docker-compose -f docker-compose-localstack.yml down
```

To remove all data:

```bash
docker-compose -f docker-compose-localstack.yml down -v
rm -rf localstack-data
```

## Troubleshooting

### LocalStack not starting

- Ensure Docker is running: `docker ps`
- Check Docker logs: `docker logs evplus-localstack`
- Verify port 4566 is not already in use: `lsof -i :4566`

### Resources not created

- Check initialization script ran: `docker logs evplus-localstack | grep "initialization"`
- Manually run init commands using `awslocal`

### Application can't connect to LocalStack

- Verify LocalStack is running: `curl http://localhost:4566/_localstack/health`
- Check application is using `local` profile
- Verify `application-local.yml` has correct LocalStack endpoints

### S3 upload fails

```bash
# Create bucket manually
awslocal s3 mb s3://ev-plus-reports-local

# Verify bucket exists
awslocal s3 ls
```

### SQS message not received

```bash
# Check queue exists
awslocal sqs list-queues

# Receive messages manually
awslocal sqs receive-message --queue-url http://localhost:4566/000000000000/ev-plus-reporting-local-queue
```

## Benefits of LocalStack

✅ No AWS costs for local development
✅ No VPN or network access required
✅ Fast iteration cycle
✅ Works completely offline
✅ Same code paths as production
✅ Full async workflow testing
