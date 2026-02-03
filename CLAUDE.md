# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**EV+ Async Reporting Service** - A Spring Boot 3.3.5 microservice for generating and delivering district-level reports asynchronously using Java 21, MySQL, Redis, AWS SQS, S3, and SES.

The service supports both synchronous (small datasets) and asynchronous (large datasets) report generation with automatic routing based on configurable thresholds. Reports are delivered via email with secure download tokens to prevent Microsoft Outlook Safe Links URL corruption.

## Build, Run, and Test Commands

### Building
```bash
# Clean build with tests
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Build Docker image
docker build -f docker/Dockerfile -t eval-pd-report-service:latest .
```

### Running Locally
```bash
# Prerequisites: Start LocalStack, MySQL, and Redis first
docker-compose -f docker-compose-localstack.yml up -d

# Run with local profile (default)
mvn spring-boot:run -Dspring.profiles.active=local

# Run with specific profile
mvn spring-boot:run -Dspring.profiles.active=dev
```

### Testing
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=ActivityByUserReportHandlerTest

# Run tests matching pattern
mvn test -Dtest=*IntegrationTest

# Generate coverage report
mvn clean test jacoco:report
# View at: target/site/jacoco/index.html

# Coverage requirements: Overall ≥80%, Service layer ≥85%, Handler layer ≥90%
```

### LocalStack Commands
```bash
# Check LocalStack health
curl http://localhost:4566/_localstack/health

# List S3 reports (with aliases from README recommended)
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test \
  aws --endpoint-url=http://localhost:4566 s3 ls \
  s3://ev-plus-reports-local/reports/ --recursive

# Check SQS queue depth
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
  aws --endpoint-url=http://localhost:4566 sqs get-queue-attributes \
  --queue-url "http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/ev-plus-reporting-local-queue" \
  --attribute-names ApproximateNumberOfMessages
```

## Core Architecture Patterns

### Handler Registry Pattern
The service uses a **Strategy + Registry** pattern for extensible report generation:

- **ReportHandler interface** (`service/handler/ReportHandler.java`): Defines contract with `getReportType()`, `validateCriteria()`, `exceedsAsyncThreshold()`, `generateReport()`, `getCriteriaClass()`
- **HandlerRegistry** (`service/handler/HandlerRegistry.java`): Auto-discovers and registers all handler implementations via Spring DI
- **ReportGeneratorService** (`service/ReportGeneratorService.java`): Orchestrates the entire workflow - looks up handler, validates criteria, checks thresholds, routes to sync/async processing

To add a new report type:
1. Create criteria class extending `ReportCriteria`
2. Create data class extending `ReportData`
3. Create handler implementing `ReportHandler` and annotate with `@Service`
4. Add enum to `ReportType`
5. Handler is automatically registered on startup

### Database Routing
The application uses **read/write cluster routing** for MySQL:
- Write operations go to primary cluster
- Read operations (queries) route to read replicas
- Configuration in `DatabaseRoutingConfig.java`
- Separate datasource properties: `spring.datasource.*` (write) and `spring.datasource.read.*` (read)

### Download Token System
To prevent Microsoft Outlook Safe Links from corrupting S3 presigned URLs:
- Service generates presigned S3 URL (7-day expiration)
- Creates short random token stored in `download_tokens` table
- Email contains clean redirect URL: `https://service.com/r/{token}`
- `RedirectController` handles `/r/{token}` (public endpoint) and redirects to S3
- Tokens expire with presigned URLs and are auto-cleaned daily at 2 AM
- See `DOWNLOAD_TOKEN_IMPLEMENTATION.md` for full details

### Notification System
Uses a **mock notification queue service** pattern:
- `NotificationQueueService` interface defines contract
- `NotificationQueueServiceImpl` (production): Sends messages to SQS notification queue
- `MockNotificationQueueService` (testing/local): Logs notifications without requiring SQS
- Active implementation selected via `@ConditionalOnProperty` based on environment
- Notifications sent for async report completion with report metadata

### Configuration Management
- **Local profile**: All credentials in `application-local.yml`, uses LocalStack
- **Dev/Stage/Prod**: Credentials loaded from AWS Secrets Manager at `/secret/eval-pd-report-service/{profile}`
- **OAuth2/OIDC**: Disabled in local, enabled in other profiles with JWT validation
- Environment-specific config files: `application-{profile}.yml`

## Key Implementation Details

### Async Processing Flow
1. User requests report via POST `/reports/generate`
2. `ReportGeneratorService` validates criteria and checks threshold
3. If exceeds threshold: Creates `ReportJob` record, sends SQS message, returns job ID
4. `AsyncReportProcessor` (SQS listener) receives message
5. Processes report, generates Excel via Apache POI SXSSF streaming
6. Uploads to S3, generates presigned URL, creates download token
7. Sends notification via notification queue service
8. Updates job status to COMPLETED (or FAILED on error)

### Excel Generation
- Uses Apache POI 5.2.5 with SXSSF for memory-efficient streaming
- `ExcelGenerationService` creates workbooks from `ReportData`
- Supports multiple report types via polymorphism
- Auto-sizing columns, bold headers, data formatting

### Error Handling
- `@ControllerAdvice` global exception handler in `exception/GlobalExceptionHandler.java`
- Custom exceptions: `ValidationException`, `ResourceNotFoundException`, `ReportGenerationException`
- Standardized `ErrorResponse` DTO with correlationId, timestamp, field errors
- `CorrelationIdFilter` adds X-Correlation-ID to all requests for tracing

### Logging
- **Local**: Plain text console logging with DEBUG level
- **Dev/Stage/Prod**: JSON structured logging via Logstash encoder
- AOP logging aspect (`aspect/LoggingAspect.java`) for controller entry/exit
- All logs include: correlationId, userId (when authenticated), reportType, timestamp

### Security
- **Local**: Security disabled via `@Profile("local")` conditional beans
- **Other profiles**: OAuth2 JWT resource server with OIDC discovery
- JWK set URI: `https://tp3a.goteachpoint.com/.well-known/openid-configuration/jwks`
- Download tokens use cryptographically secure random (24 bytes, 192 bits)
- Separate JWT tokens for download links (7-day expiration, RS256 signing)

## Database Schema

### Key Tables
- **reportjobs**: Tracks async report requests (report_id, district_id, user_id, report_type, status, s3_url, etc.)
- **threshold_configs**: Configurable sync/async thresholds per report type (max_records, max_duration_seconds)
- **download_tokens**: Token-to-presigned-URL mappings (token, presigned_url, expires_at, access_count)
- **notification_events**: Notification queue messages (event_type, payload, status)
- **notification_queue**: Queue metadata

### Migrations
- Flyway migrations in `src/main/resources/db/migration/`
- Manual patches in `src/main/resources/db/patch*.sql` (coordinate with DBA before running)

## API Endpoints

### Report Generation
- `POST /ev-pd-report/v1/reports/generate` - Generate report (sync or async based on threshold)
- `GET /ev-pd-report/v1/reports/status/{jobId}` - Check async job status
- `GET /ev-pd-report/v1/reports/download/{jobId}` - Download completed report (authenticated)

### Public Redirect
- `GET /r/{token}` - Public redirect to S3 presigned URL (no authentication)

### Admin
- `GET /ev-pd-report/v1/admin/thresholds/{reportType}` - Get threshold config
- `PUT /ev-pd-report/v1/admin/thresholds/{reportType}` - Update threshold config

### Actuator
- `GET /ev-pd-report/v1/actuator/health` - Health check (database, Redis)
- `GET /ev-pd-report/v1/actuator/metrics` - Application metrics
- `GET /ev-pd-report/v1/actuator/info` - Application info

### Developer Tools (Local Only)
- `POST /ev-pd-report/v1/dev/jwt/generate` - Generate test JWT tokens for local development

## Testing Strategy

### Unit Tests
- Mock all external dependencies (repositories, AWS services, Redis)
- Test business logic in isolation
- Use JUnit 5, Mockito, AssertJ
- Target: ≥80% overall, ≥85% service layer, ≥90% handler layer

### Integration Tests
- Use Testcontainers for MySQL and Redis
- Use LocalStack container for S3, SQS, SES
- Test end-to-end flows including database and AWS services
- Annotate with `@SpringBootTest` and `@Testcontainers`

### LocalStack Integration
- Tests automatically connect to LocalStack for AWS services
- Ensure LocalStack is running: `docker ps | grep localstack`
- Integration tests will fail if LocalStack is not available

## Important Notes

### When Adding New Report Types
1. Follow the handler pattern - implement `ReportHandler` interface
2. Add enum value to `ReportType`
3. Create criteria and data DTOs extending base classes
4. Handler auto-registers on startup (no manual registration needed)
5. Add threshold config in database via admin endpoint or SQL insert

### AWS Secrets Manager (Dev/Stage/Prod)
- Secret name: `/secret/eval-pd-report-service/{profile}`
- Must contain JSON with Spring property keys:
  - `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password`
  - `spring.datasource.read.url`, `spring.datasource.read.username`, `spring.datasource.read.password`
  - `spring.data.redis.host`, `spring.data.redis.port`, `spring.data.redis.password`
  - `app.download-token.jwt.private-key`, `app.download-token.jwt.public-key`
- See `.notes/aws-secrets-manager-setup.md` for setup instructions

### Database Query Optimization
- Use JPA projections (not full entities) for performance
- Required indexes exist on: activity_date, activity_user_date, activity_type
- Queries include LIMIT clauses to prevent runaway queries
- All queries use parameterized statements (no string concatenation)

### Code Coverage Enforcement
- JaCoCo Maven plugin enforces ≥80% coverage
- Build fails if coverage requirements not met
- Service layer should maintain ≥85%, handlers ≥90%

### LocalStack vs Real AWS
- Local profile uses LocalStack endpoints: `http://localhost:4566`
- Other profiles use real AWS (credentials via IRSA or environment variables)
- S3 bucket names differ: `ev-plus-reports-local` vs `ev-plus-reports-{env}`
- SQS queue names differ: `ev-plus-reporting-local-queue` vs `eval-pd-{env}-report-service-queue`

### CORS Configuration
- CORS settings in `application.yml` under `cors.*`
- Default allows all origins in local (`*`)
- Override via `CORS_ALLOWED_ORIGINS` environment variable for specific environments

## Troubleshooting

### "Download token not found" errors
- Check `APP_BASE_URL` is set correctly in environment
- Verify download_tokens table exists and has records
- Tokens expire after 7 days - request new report
- See `DOWNLOAD_TOKEN_IMPLEMENTATION.md` troubleshooting section

### SQS messages not processing
- Check SQS listener is enabled: `spring.cloud.aws.sqs.enabled=true`
- Verify queue URL is correct in configuration
- Check application has IAM permissions for SQS
- View logs with DEBUG level: `io.awspring.cloud.sqs: DEBUG`

### Integration tests failing
- Ensure LocalStack is running: `docker ps | grep localstack`
- Check MySQL and Redis containers are up
- Stop conflicting Docker environments (Rancher Desktop, Podman)
- Verify `/var/run/docker.sock` is accessible

### Database connection errors
- Local: Verify MySQL on localhost:3306 with credentials tpuser/tppass1
- Dev/Stage/Prod: Check AWS Secrets Manager secret exists and has correct values
- Verify security groups allow traffic from EKS to RDS/ElastiCache

## Implementation Guidelines

Refer to `IMPLEMENTATION_GUIDELINES.md` for high-level implementation patterns and `IMPLEMENTATION_PLAN.md` for detailed phase-by-phase specifications. The codebase follows Spring Boot best practices with clean code principles and modern Java patterns.
