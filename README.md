# EV+ Async Reporting Service

Microservice for generating and delivering district-level reports asynchronously using Spring Boot 3.5 and Java 21.

## Features

- **Synchronous Reports**: Instant generation for small datasets (<5000 records or <10 seconds)
- **Asynchronous Processing**: SQS-based background processing for large reports
- **Email Notifications**: Presigned S3 URLs delivered via email (7-day expiration)
- **Excel Generation**: Apache POI with SXSSF streaming for memory-efficient large files
- **OAuth2/OIDC Authentication**: JWT-based security (disabled in local development)
- **Read/Write Database Routing**: Automatic routing to read replicas for queries
- **AWS Secrets Manager**: Secure credential management for all environments
- **LocalStack Support**: Full local development without AWS costs

## Tech Stack

- **Java**: 21
- **Spring Boot**: 3.3.5
- **Spring Cloud AWS**: 3.1.1
- **Database**: MySQL 8.0+ with read/write cluster routing
- **Cache**: Redis (ElastiCache Valkey in AWS)
- **Message Queue**: AWS SQS (Standard, not FIFO)
- **Storage**: AWS S3
- **Email**: AWS SES
- **Secrets**: AWS Secrets Manager
- **API Docs**: SpringDoc OpenAPI 3.0 (Swagger UI)
- **Excel**: Apache POI 5.2.5
- **Logging**: Logstash encoder for JSON structured logging

## Prerequisites

### For Local Development

- Java 21 (JDK)
- Maven 3.8+
- MySQL 8.0+ running on localhost:3306
  - Database: `teachpoint`
  - Credentials: `tpuser/tppass1`
- Redis running on localhost:6379
- Docker (for LocalStack)

### For AWS Environments (Dev/Stage/Prod)

- AWS CLI configured
- Access to AWS account with appropriate permissions
- IRSA (IAM Roles for Service Accounts) configured for EKS
- AWS Secrets Manager secrets created (see below)

## Quick Start

### 1. Clone and Build

```bash
cd eval-pd-report-service
mvn clean install
```

### 2. Start LocalStack (for local development)

```bash
docker-compose -f docker-compose-localstack.yml up -d
```

Verify LocalStack is running:

```bash
curl http://localhost:4566/_localstack/health
```

### 3. Run Application Locally

```bash
mvn spring-boot:run -Dspring.profiles.active=local
```

Or with your IDE, set active profile to `local`.

### 4. Access Application

- **Application**: http://localhost:8080/vector-eval/v1/eval-pd-report
- **Swagger UI**: http://localhost:8080/vector-eval/v1/eval-pd-report/swagger-ui.html
- **Health Check**: http://localhost:8080/vector-eval/v1/eval-pd-report/actuator/health

## Configuration

### Environment Profiles

The application supports 4 environment profiles:

| Profile | Description | Authentication | AWS Services |
|---------|-------------|----------------|--------------|
| `local` | Local development | Disabled | LocalStack |
| `dev` | Development | OAuth2 OIDC | Real AWS (Secrets Manager) |
| `stage` | Staging | OAuth2 OIDC | Real AWS (Secrets Manager) |
| `prod` | Production | OAuth2 OIDC | Real AWS (Secrets Manager) |

### Local Development Configuration

For local development, all credentials are stored in `application-local.yml`:

```yaml
# Database
spring.datasource.url: jdbc:mysql://localhost:3306/teachpoint
spring.datasource.username: tpuser
spring.datasource.password: tppass1

# Redis
spring.data.redis.host: localhost
spring.data.redis.port: 6379

# AWS Services (LocalStack)
AWS endpoints: http://localhost:4566
Credentials: test/test (dummy credentials for LocalStack)
```

### AWS Secrets Manager Setup (Dev/Stage/Prod)

For non-local environments, all sensitive configuration is loaded from AWS Secrets Manager.

**Secret Naming Convention**: `/secret/eval-pd-report-service/{environment}`

**Required Secrets JSON Structure**:

```json
{
  "spring.datasource.url": "jdbc:mysql://write-cluster:3306/evplus",
  "spring.datasource.username": "evplus_user",
  "spring.datasource.password": "SECRET_PASSWORD",
  "spring.datasource.read.url": "jdbc:mysql://read-cluster:3306/evplus",
  "spring.datasource.read.username": "evplus_read_user",
  "spring.datasource.read.password": "SECRET_READ_PASSWORD",
  "spring.data.redis.host": "redis-cluster.cache.amazonaws.com",
  "spring.data.redis.port": "6379",
  "spring.data.redis.password": "SECRET_REDIS_PASSWORD"
}
```

**Create secrets using AWS CLI**:

```bash
# Development
aws secretsmanager create-secret \
  --name /secret/eval-pd-report-service/dev \
  --secret-string file://secrets-dev.json \
  --region us-east-1

# Staging
aws secretsmanager create-secret \
  --name /secret/eval-pd-report-service/stage \
  --secret-string file://secrets-stage.json \
  --region us-east-1

# Production
aws secretsmanager create-secret \
  --name /secret/eval-pd-report-service/prod \
  --secret-string file://secrets-prod.json \
  --region us-east-1
```

See `.notes/aws-secrets-manager-setup.md` for detailed instructions.

### OAuth2/OIDC Configuration

**Local Environment**: Authentication is disabled.

**Dev/Stage/Prod Environments**: Configure OIDC issuer URI in environment variables or AWS Secrets Manager:

```yaml
spring.security.oauth2.resourceserver.jwt.issuer-uri: https://your-oidc-provider.com
```

The application will auto-discover OIDC configuration from `{issuer-uri}/.well-known/openid-configuration`.

## API Endpoints

### Reports

- **POST /reports/generate** - Generate report (sync or async based on size)
- **GET /reports/status/{jobId}** - Check async report status
- **GET /reports/download/{jobId}** - Download completed report

### Admin

- **GET /admin/thresholds/{reportType}** - Get threshold configuration
- **PUT /admin/thresholds/{reportType}** - Update threshold configuration

### Actuator

- **GET /actuator/health** - Health check (database, Redis)
- **GET /actuator/info** - Application info
- **GET /actuator/metrics** - Application metrics

## Database Schema

Phase 1 includes application bootstrap only. Database schema will be created in Phase 2.

**Required Tables** (to be created in Phase 2):
- `reportjobs` - Tracks all report generation requests
- `threshold_configs` - Configurable sync/async thresholds per report type

Schema patch location: `src/main/resources/db/patch20260128_tp_17281_reportjobs.sql`

## LocalStack Usage

LocalStack provides local AWS service emulation for development and testing.

### Start LocalStack

```bash
docker-compose -f docker-compose-localstack.yml up -d
```

### Verify Resources

```bash
# List S3 buckets
awslocal s3 ls

# List SQS queues
awslocal sqs list-queues

# Check health
curl http://localhost:4566/_localstack/health
```

### Stop LocalStack

```bash
docker-compose -f docker-compose-localstack.yml down
```

See `.notes/localstack-setup.md` for detailed LocalStack documentation.

## Building and Testing

### Build

```bash
mvn clean install
```

### Run Tests

```bash
mvn test
```

### Code Coverage

```bash
mvn clean test jacoco:report
```

Coverage report: `target/site/jacoco/index.html`

**Coverage Requirements**:
- Overall: ≥80%
- Service layer: ≥85%
- Handler layer: ≥90%

## Docker Build

```bash
# Build Docker image
docker build -t eval-pd-report-service:latest .

# Run with Docker
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  eval-pd-report-service:latest
```

## Deployment

### Kubernetes Deployment

The application is designed to run on Amazon EKS with:
- IRSA (IAM Roles for Service Accounts) for AWS permissions
- HorizontalPodAutoscaler for auto-scaling (3-10 replicas)
- Liveness and Readiness probes configured

See Phase 0 and Phase 7 of the implementation plan for infrastructure and deployment details.

### Environment Variables

Required for non-local environments:

```bash
SPRING_PROFILES_ACTIVE=dev|stage|prod
OIDC_ISSUER_URI=https://your-oidc-provider.com
S3_BUCKET_NAME=ev-plus-reports-{env}
SQS_QUEUE_NAME=ev-plus-reporting-{env}-queue
EMAIL_FROM_ADDRESS=noreply@evplus.com
```

## Monitoring and Observability

### Health Checks

- **Liveness**: `/actuator/health/liveness`
- **Readiness**: `/actuator/health/readiness`
- **Full Health**: `/actuator/health`

Custom health indicators verify:
- Database connectivity (write and read clusters)
- Redis connectivity and response time

### Logging

**Local Environment**: Plain text console logging with DEBUG level

**Dev/Stage/Prod**: JSON structured logging with Logstash encoder for log aggregation

All logs include:
- `correlationId` - Request tracing ID (from X-Correlation-ID header or auto-generated)
- `userId` - Authenticated user ID (when available)
- `reportType` - Report type being processed (when applicable)
- `timestamp` - ISO 8601 timestamp
- `level` - Log level
- `message` - Log message
- `logger` - Logger name

### Metrics

Actuator metrics available at `/actuator/metrics`:
- JVM metrics (memory, threads, GC)
- HTTP request metrics
- Database connection pool metrics
- Cache hit/miss rates

Custom business metrics (Phase 6):
- Report generation duration (sync/async)
- Sync vs async threshold decisions
- Failed job counts by error type

## Troubleshooting

### Application won't start

**Check Java version**:
```bash
java -version  # Should be Java 21
```

**Check MySQL is running**:
```bash
mysql -h localhost -u tpuser -p teachpoint -e "SELECT 1"
```

**Check Redis is running**:
```bash
redis-cli ping  # Should return PONG
```

### LocalStack not working

**Check Docker is running**:
```bash
docker ps | grep localstack
```

**View LocalStack logs**:
```bash
docker logs evplus-localstack
```

**Restart LocalStack**:
```bash
docker-compose -f docker-compose-localstack.yml restart
```

### Database connection errors

**Verify credentials** in `application-local.yml`

**Check database exists**:
```bash
mysql -u tpuser -ptppass1 -e "SHOW DATABASES LIKE 'teachpoint'"
```

**Create database if needed**:
```bash
mysql -u tpuser -ptppass1 -e "CREATE DATABASE IF NOT EXISTS teachpoint"
```

### Redis connection errors

**Check Redis is running**:
```bash
redis-cli ping
```

**Start Redis** (if not running):
```bash
redis-server
```

### AWS Secrets Manager errors (Dev/Stage/Prod)

**Verify secret exists**:
```bash
aws secretsmanager describe-secret \
  --secret-id /secret/eval-pd-report-service/dev \
  --region us-east-1
```

**Check IAM permissions**: Ensure IRSA role has `secretsmanager:GetSecretValue` permission.

## Project Structure

```
eval-pd-report-service/
├── src/
│   ├── main/
│   │   ├── java/com/evplus/report/
│   │   │   ├── aspect/              # AOP logging aspects
│   │   │   ├── config/              # Spring configuration
│   │   │   │   ├── health/          # Custom health indicators
│   │   │   │   ├── DatabaseRoutingConfig.java
│   │   │   │   ├── OpenApiConfig.java
│   │   │   │   └── ...
│   │   │   ├── controller/          # REST controllers
│   │   │   ├── exception/           # Custom exceptions
│   │   │   ├── model/               # Domain models
│   │   │   │   ├── dto/             # Data transfer objects
│   │   │   │   ├── entity/          # JPA entities
│   │   │   │   └── enums/           # Enumerations
│   │   │   ├── repository/          # Spring Data repositories
│   │   │   ├── security/            # Security configuration
│   │   │   ├── service/             # Business logic
│   │   │   ├── util/                # Utility classes
│   │   │   └── EvalPdReportServiceApplication.java
│   │   └── resources/
│   │       ├── application.yml      # Base configuration
│   │       ├── application-local.yml
│   │       ├── application-dev.yml
│   │       ├── application-stage.yml
│   │       ├── application-prod.yml
│   │       ├── logback-spring.xml   # Logging configuration
│   │       └── db/                  # Database patches (Phase 2)
│   └── test/                        # Unit and integration tests
├── .notes/                          # Working notes and documentation
├── docker-compose-localstack.yml    # LocalStack setup
├── localstack-init.sh               # LocalStack initialization script
├── pom.xml                          # Maven dependencies
├── IMPLEMENTATION_PLAN.md           # Full implementation plan
└── README.md                        # This file
```

## Development Workflow

1. **Start LocalStack**: `docker-compose -f docker-compose-localstack.yml up -d`
2. **Start MySQL and Redis**: Ensure both are running locally
3. **Run Application**: `mvn spring-boot:run -Dspring.profiles.active=local`
4. **Access Swagger UI**: http://localhost:8080/vector-eval/v1/eval-pd-report/swagger-ui.html
5. **Make Changes**: Edit code, tests auto-reload with Spring DevTools
6. **Run Tests**: `mvn test`
7. **Check Coverage**: `mvn jacoco:report`

## Contributing

### Code Style

- Follow Java 21 best practices
- Use Lombok for boilerplate reduction
- Write comprehensive JavaDoc for public APIs
- Maintain test coverage ≥80%

### Git Workflow

1. Create feature branch from `main`
2. Make changes and commit with meaningful messages
3. Run tests: `mvn clean test`
4. Push and create pull request
5. Code review required before merge

## Support

For questions or issues:
- Check `.notes/` directory for detailed documentation
- Review `IMPLEMENTATION_PLAN.md` for architecture details
- Contact: evplus-dev@example.com

## License

Proprietary - EV+ Development Team

## Implementation Progress

**Current Phase**: Phase 1 - Service Bootstrap & Configuration
**Status**: ✅ COMPLETED

See `IMPLEMENTATION_PLAN.md` for full project timeline and progress tracking.
