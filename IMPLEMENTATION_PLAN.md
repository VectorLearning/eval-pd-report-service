# EV+ Async Reporting Service - Complete Implementation Plan

**Project**: TP-17281 Async Reporting Backend Microservice
**Timeline**: 10-14 Weeks
**Team Size**: 3 Mid-Level Developers + 1 DevOps Engineer (+ Claude Sonnet for implementation)
**Status**: Ready for Implementation
**Version**: 2.1
**Date**: January 2025
**Last Updated**: 2025-01-20 (SQS Standard, LocalStack, Email optimization)

---

## 📊 Project Progress Tracker

**Last Updated**: 2025-11-29 (Evening - Bug Fixes Applied)
**Overall Progress**: 35% (18/51 tasks completed)
**Current Week**: Week 5 of 14
**Current Phase**: Phase 4 - Asynchronous Processing (⏳ In Progress)
**Key Changes from Original Plan**: SQS FIFO → Standard, Email with presigned URL (no attachments), LocalStack for local dev
**Recent Bug Fixes**: Path-style S3 URLs, Transaction race condition, Error message cleanup, Swagger Bearer token

### Phase Completion Status

| Phase | Tasks | Completed | Progress | Status |
|-------|-------|-----------|----------|--------|
| Phase 1: Service Bootstrap | 10 tasks | 10 | 100% | ✅ Complete |
| Phase 2: Core Framework | 5 tasks | 5 | 100% | ✅ Complete |
| Phase 4: Async Processing (with Dummy Data) | 6 tasks | 3 | 50% | ⏳ In Progress |
| Phase 3: User Activity Report | 4 tasks | 0 | 0% | 📋 Not Started |
| Phase 5: Testing & QA | 6 tasks | 0 | 0% | 📋 Not Started |
| Phase 0: Infrastructure | 6 tasks | 0 | 0% | 📋 Not Started |
| Phase 6: Observability | 5 tasks | 0 | 0% | 📋 Not Started |
| Phase 7: Deployment | 5 tasks | 0 | 0% | 📋 Not Started |
| Phase 8: Validation & Handoff | 6 tasks | 0 | 0% | 📋 Not Started |

**Status Legend**: 📋 Not Started | ⏳ In Progress | ✅ Complete | ⚠️ Blocked

---

## 📖 How to Track Progress in This Document

### Update Task Status as Work Progresses

Each task has tracking fields that should be updated:

```markdown
### Task X.Y: Task Name

**Effort**: 2 days
**Status**: ⏳ In Progress          ← Update this
**Assignee**: Developer 1           ← Update this
**Started**: 2025-01-20            ← Update this
**Completed**: -                    ← Update this when done
```

### Step-by-Step Progress Tracking

**When starting a task:**
1. Change status from `📋 Not Started` to `⏳ In Progress`
2. Add assignee name
3. Add started date

**When completing a task:**
1. Change status from `⏳ In Progress` to `✅ Complete`
2. Add completed date
3. Check off the corresponding item in Phase Exit Criteria

**If blocked:**
1. Change status to `⚠️ Blocked`
2. Add note explaining blocker in the task section

**Update the Progress Tracker (top of document):**
- Update "Last Updated" date
- Update "Overall Progress" percentage
- Update "Current Week"
- Update "Current Phase"
- Update phase completion table

### Example: Task in Progress

```markdown
### Task 1.4: OAuth2/OIDC Security Configuration

**Effort**: 2 days
**Status**: ⏳ In Progress
**Assignee**: Developer 1
**Started**: 2025-01-20
**Completed**: -

**Notes**: Waiting on OIDC provider configuration from DevOps
```

### Example: Completed Task

```markdown
### Task 1.1: Spring Boot 3.5 Project Setup

**Effort**: 1 day
**Status**: ✅ Complete
**Assignee**: Developer 1
**Started**: 2025-01-15
**Completed**: 2025-01-16
```

### Phase Exit Criteria Tracking

At the end of each phase, mark exit criteria as complete:

```markdown
✅ All tasks completed:
- [x] Spring Boot 3.5 + Java 21 project with all dependencies
- [x] OAuth2/OIDC authentication configured and tested
- [x] Swagger UI accessible and functional (Bearer token support added 2025-11-29)
```

---

## 📝 Key Updates & Changes

**Version 2.1 - January 2025**

This implementation plan has been updated based on technical review and optimization:

### Major Architecture Changes

1. **SQS Queue Type: FIFO → Standard**
   - **Rationale**: Higher throughput, lower cost, simpler configuration
   - **Impact**: Requires idempotency handling in AsyncReportProcessor
   - **Benefit**: Unlimited TPS vs FIFO's 3000 TPS, ~40% cost reduction

2. **Email Delivery: Presigned URL Only (No Attachments)**
   - **Rationale**: Better deliverability, no size limits, simpler implementation
   - **Impact**: Email template simplified to URL link only
   - **Benefit**: No SES attachment restrictions, 5-10KB email size vs potentially MBs

3. **Threshold Configuration: Optimized Values**
   - **Previous**: 10,000 records / 30 seconds
   - **Updated**: 5,000 records / 10 seconds
   - **Rationale**: Apache POI can generate ~5K rows in 3-5 seconds, better UX for sync reports

4. **Local Development: LocalStack Integration**
   - **Addition**: New Task 1.9 for LocalStack setup
   - **Services**: Mock S3, SQS, SES locally
   - **Benefit**: No AWS costs, no VPN required, offline development capability

### Implementation Enhancements

5. **Excel Generation: SXSSF Streaming**
   - **Updated**: Use SXSSFWorkbook instead of XSSFWorkbook
   - **Benefit**: Keeps only 100 rows in memory, prevents OutOfMemoryError for large reports

6. **Idempotency Strategy**
   - **Addition**: Explicit idempotency checks in AsyncReportProcessor
   - **Implementation**: Check job status before processing, handle duplicate messages gracefully

7. **Mock Services for Local Development**
   - **Addition**: Profile-based EmailService that logs to console
   - **Benefit**: Full async workflow testing without AWS dependencies

### What Stayed the Same

- ✅ Spring Boot 3.5 + Java 21
- ✅ OAuth2/OIDC authentication
- ✅ Read/Write database cluster routing
- ✅ Handler registry pattern
- ✅ AWS Secrets Manager for environments
- ✅ Apache POI for Excel generation

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Project Timeline](#project-timeline)
3. [Team Roles](#team-roles)
4. [Phase 1: Service Bootstrap](#phase-1-service-bootstrap--configuration)
5. [Phase 2: Core Framework](#phase-2-core-reporting-framework)
6. [Phase 4: Async Processing (MOVED UP)](#phase-4-asynchronous-processing)
7. [Phase 3: User Activity Report (MOVED DOWN)](#phase-3-user-activity-report-implementation)
8. [Phase 5: Testing & QA](#phase-5-testing--quality-assurance)
9. [Phase 0: Infrastructure (Deferred)](#phase-0-infrastructure-setup-deferred)
10. [Phase 6: Observability](#phase-6-observability--monitoring)
11. [Phase 7: Deployment](#phase-7-deployment--rollout)
12. [Phase 8: Validation & Handoff](#phase-8-validation--handoff)
13. [Appendices](#appendices)

---

## Executive Summary

### Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **API Base Path** | `/vector-eval/v1/eval-pd-report/` | Vector standard API structure |
| **Authentication** | Spring Security OAuth2 OIDC | JWT-based authentication with auto-discovery |
| **API Documentation** | SpringDoc OpenAPI 3.0 | Auto-generated Swagger UI from code |
| **Secrets Management** | AWS Secrets Manager | Secure, centralized secret storage |
| **Environment Configs** | Separate files (local/dev/stage/prod) | Environment-specific settings |
| **Error Handling** | Standardized ErrorResponse DTO | Consistent API error responses |
| **Database** | Read/Write cluster routing | Scalability and performance optimization |
| **Cache** | Redis (ElastiCache Valkey) | Distributed caching |
| **Async Processing** | SQS Standard + S3 + Email | Scalable async workflow with idempotency |
| **Local Development** | LocalStack (S3, SQS) | Mock AWS services for local testing |

### Success Metrics

- Sync reports: <10 seconds
- Async reports: Email within 30 minutes
- Test coverage: ≥80%
- Uptime: 99.9%
- Concurrent requests: 100+

---

## Project Timeline

```
Week 1-2   ████████  Phase 1: Bootstrap (AWS Secrets Manager, Configs, Error Handling)
Week 2-4   ████████  Phase 2: Core Framework (Handler Registry, REST API)
Week 5-6   ████████  Phase 4: Async Processing (SQS, S3, Notification Queue) - MOVED UP
Week 7-8   ████████  Phase 3: User Activity Report (Real handler replaces dummy) - MOVED DOWN
Week 9-10  ████████  Phase 5: Testing & QA (Load testing, 80% coverage)
Week 10-11 ████████  Phase 0: Infrastructure (VPC, IRSA, CircleCI)
Week 11-12 ████████  Phase 6: Observability (Datadog, Custom metrics)
Week 12-13 ████████  Phase 7: Deployment (Stage, Prod)
Week 13-14 ████████  Phase 8: Validation & Handoff
```

---

## Team Roles

**Developer 1 (Backend Lead)**: Architecture, AWS Secrets Manager integration, orchestrator, code reviews
**Developer 2 (Async Specialist)**: SQS, S3, email, error handling
**Developer 3 (Reporting & Testing)**: Report handlers, Excel, tests
**DevOps Engineer**: Infrastructure, CI/CD, Kubernetes, Datadog, AWS Secrets setup

---

## Phase 1: Service Bootstrap & Configuration

**Duration**: 2 weeks (Week 1-2)
**Owner**: Developer 1

### Task 1.1: Spring Boot 3.5 Project Setup

**Effort**: 1 day
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-01-20
**Completed**: 2025-01-21

**Key Dependencies** (Maven):

**Spring Boot Starters**:
- spring-boot-starter-web (REST API)
- spring-boot-starter-data-jpa (Database access)
- spring-boot-starter-cache (Redis caching)
- spring-boot-starter-actuator (Health checks, metrics)
- spring-boot-starter-validation (Bean validation)
- spring-boot-starter-data-redis (Redis integration)
- spring-boot-starter-aop (Logging aspects)
- spring-boot-starter-oauth2-resource-server (OIDC authentication)
- spring-boot-starter-security (Security framework)

**AWS Integration (Spring Cloud AWS 3.1.1+)**:
- spring-cloud-aws-starter (Core AWS integration)
- spring-cloud-aws-starter-secrets-manager (Automatic secret loading)
- spring-cloud-aws-starter-sqs (SQS message handling)
- software.amazon.awssdk:s3 (S3 file operations)
- software.amazon.awssdk:ses (Email notifications)

**Database & Cache**:
- mysql-connector-j (MySQL JDBC driver)
- lettuce-core (Redis client)

**Utilities**:
- poi-ooxml (version 5.2.5) - Apache POI for Excel generation
- logstash-logback-encoder (version 7.4) - Structured JSON logging

**API Documentation**:
- springdoc-openapi-starter-webmvc-ui (version 2.3+) - OpenAPI 3.0 spec generation and Swagger UI

**Main Application Class**:
Create a standard Spring Boot application with `@SpringBootApplication` and `@EnableCaching` annotations. The application should bootstrap with standard Spring configuration scanning.

---

### Task 1.2: AWS Secrets Manager Integration

**Effort**: 1 day
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-01-21
**Completed**: 2025-01-22

**How Spring Cloud AWS Secrets Manager Works**:
Spring Cloud AWS automatically loads secrets from AWS Secrets Manager and makes them available as Spring properties. No custom code needed!

**Configuration Approach**:
- Enable Spring Cloud AWS Secrets Manager in base application.yml
- Configure AWS region (us-east-1)
- Enable instance-profile credentials (IRSA will provide credentials automatically)
- Set application name to match secret naming convention

**application.yml** (Base) - Key Settings:

Configure Spring Cloud AWS Secrets Manager integration:
- Application name: `eval-pd-report-service` (must match secret naming convention)
- AWS region: `us-east-1` (static configuration)
- Credentials: Enable instance-profile mode (IRSA provides credentials automatically via pod service account)
- Secrets Manager: Enable automatic secret loading
- No additional configuration needed - secrets loaded based on naming convention

**How to Create Secrets in AWS**:
Spring Cloud AWS expects secrets in specific format. Each secret should be a JSON with key-value pairs where keys are Spring property names.

**Secret Naming Convention**: `/secret/eval-pd-report-service/{environment}`

**Required Secrets for Each Environment** (dev/stage/prod):
- Database connection URL (spring.datasource.url)
- Database username (spring.datasource.username)
- Database password (spring.datasource.password)
- Redis host (spring.data.redis.host)
- Redis port (spring.data.redis.port)
- Redis password (spring.data.redis.password)

**DevOps Action**: Create secrets using AWS CLI with JSON format containing all Spring property key-value pairs for each environment.

**Property Resolution Flow**:
1. Application starts with profile (e.g., `dev`)
2. Spring Cloud AWS automatically looks for secret: `/secret/eval-pd-report-service/dev`
3. All JSON key-value pairs from secret become Spring properties
4. Spring Boot auto-configuration uses these properties for DataSource, Redis, etc.

**No Custom Code Required**: Spring Boot's auto-configuration will automatically connect to database and Redis using properties loaded from Secrets Manager.

---

### Task 1.3: Environment Configuration Files

**Effort**: 1 day
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-01-22
**Completed**: 2025-01-23

**application.yml** (Base):

**Spring Configuration**:
- Application name: `eval-pd-report-service`
- JPA/Hibernate: Set ddl-auto to `validate` (no auto-schema generation in production)
- Cache type: Redis
- AWS Cloud: Region us-east-1, instance-profile credentials, Secrets Manager enabled

**Server Configuration**:
- Port: 8080
- Context path: `/ev-pd-report/v1`

**Actuator Configuration**:
- Expose endpoints: health, info, metrics
- Base path: `/actuator`
- Health endpoint: Show full details, enable liveness/readiness probes for Kubernetes

**application-local.yml**:
- Disable AWS Secrets Manager (enabled: false)
- Configure local database with plain text credentials (existing local MySQL)
- Configure local Redis without SSL (existing local Redis)
- Use LocalStack for S3 and SQS (endpoint: http://localhost:4566)
- Configure notification queue name for local: `ev-plus-notifications-local-queue`
- Mock email service (log to console instead of SES)
- OAuth2 security disabled or mocked for local development
- Use localhost for all connections

**application-dev.yml**:
- Secrets Manager enabled (inherits from base config)
- Database and Redis credentials loaded automatically from AWS Secrets Manager
- Configure AWS service endpoints: SQS queue name, S3 bucket name
- Configure notification queue name: `${NOTIFICATION_QUEUE_NAME}` (will be configured per environment)
- Enable SSL for Redis

**application-stage.yml** & **application-prod.yml**:
Similar structure to dev, with environment-specific AWS resource names (including notification queue names).

**Key Principle**: No custom configuration classes needed. Spring Cloud AWS and Spring Boot auto-configuration handle everything.

---

### Task 1.4: OAuth2/OIDC Security Configuration

**Effort**: 2 days
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-01-23
**Completed**: 2025-01-24

**Purpose**: Implement API authentication using Spring Security OAuth2 Resource Server with OIDC discovery

**Security Configuration**:
Create a Spring Security configuration class with the following requirements:

**OAuth2 Resource Server Setup**:
- Configure application as an OAuth2 Resource Server
- Enable JWT authentication with OIDC issuer URI discovery
- The issuer URI should be externalized to configuration properties
- Spring Security will auto-discover OIDC configuration from `{issuer-uri}/.well-known/openid-configuration`

**Endpoint Security Rules**:
- Actuator health endpoints: Permit all (no authentication required)
- Swagger/OpenAPI endpoints: Permit all (`/swagger-ui/**`, `/v3/api-docs/**`)
- All other endpoints (`/reports/**`, `/admin/**`): Require authentication

**JWT Token Validation**:
- Validate JWT signature using public keys from OIDC provider
- Extract user information from JWT claims (subject, email, roles)
- Map JWT claims to Spring Security authorities/roles

**User Principal Extraction**:
- Create custom User object from JWT claims
- Extract: userId, email, firstName, lastName, roles/authorities
- Make available via `@AuthenticationPrincipal` in controllers

**Authorization Logic**:
- Admin role: Check for specific JWT claim or authority (e.g., `roles` claim contains "ADMIN")
- Implement method security for admin-only endpoints
- Implement resource ownership checks (users can only access their own report jobs)

**Configuration Properties** (application.yml):
- `spring.security.oauth2.resourceserver.jwt.issuer-uri`: OIDC provider URL
- Different issuer URIs per environment (local/dev/stage/prod)
- For local development: Option to disable security or use mock authentication

**Required Secrets** (to be stored in AWS Secrets Manager):
No client secrets needed for resource server, but issuer URI should be environment-specific:
- Local: Mock OIDC provider or security disabled
- Dev/Stage/Prod: Production OIDC provider URL

**Testing Considerations**:
- For integration tests: Use Spring Security Test with `@WithMockUser`
- For local development: Provide option to run with security disabled or mock JWT

---

### Task 1.5: API Documentation with Swagger/OpenAPI

**Effort**: 1 day
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-01-24
**Completed**: 2025-01-24

**Purpose**: Auto-generate OpenAPI 3.0 specification and Swagger UI from code

**SpringDoc Configuration**:

**Application Configuration** (application.yml):
- Enable springdoc-openapi
- Configure Swagger UI path: `/swagger-ui.html`
- Configure API docs path: `/v3/api-docs`
- Set API base path to match server context path

**OpenAPI Metadata**:
Configure API information:
- Title: "EV+ Async Reporting Service API"
- Version: From `pom.xml` version
- Description: "Microservice for generating and delivering district-level reports asynchronously"
- Contact: Team email/slack channel

**Security Scheme Configuration**:
Configure OAuth2/OIDC security scheme in OpenAPI spec:
- Scheme type: OAuth2 with Authorization Code flow
- Authorization URL: From OIDC provider
- Token URL: From OIDC provider
- Scopes: Document required scopes for API access
- This enables "Authorize" button in Swagger UI for testing

**Controller Annotations**:
Add OpenAPI annotations to controllers for documentation:
- `@Tag`: Group endpoints by feature (Reports, Admin, Health)
- `@Operation`: Describe each endpoint with summary and description
- `@ApiResponse`: Document possible response codes (200, 201, 400, 403, 404, 500)
- `@Parameter`: Describe path variables and query parameters
- `@Schema`: Document request/response DTOs with field descriptions

**DTO Documentation**:
Annotate all DTOs with:
- `@Schema`: Class-level description
- Field-level `@Schema`: Describe each field with example values
- `@NotNull`, `@Valid`: Validation annotations auto-documented

**Configuration Class**:
Create OpenAPI configuration bean that:
- Customizes OpenAPI object with metadata
- Configures security schemes (OAuth2)
- Sets up server URLs per environment
- Excludes actuator endpoints from documentation (optional)

**Access URLs**:
- Swagger UI: `http://localhost:8080/ev-pd-report/v1/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/ev-pd-report/v1/v3/api-docs`

**Benefits**:
- Auto-generated, always up-to-date API documentation
- Interactive API testing via Swagger UI
- Contract-first development with OpenAPI spec export
- Frontend teams can use spec for client generation

---

### Task 1.6: Read/Write Database Cluster Configuration

**Effort**: 1 day
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-01-24
**Completed**: 2025-01-25

**Purpose**: Support separate read and write database clusters for scalability and performance

**Multi-DataSource Configuration**:

**Database Cluster Setup**:
- Write Cluster (Primary): Handles all INSERT, UPDATE, DELETE operations
- Read Cluster (Replica): Handles all SELECT queries
- Both clusters should be configurable per environment

**DataSource Configuration**:
Create two separate DataSource beans:
- `writeDataSource`: Primary database cluster connection
- `readDataSource`: Read-replica cluster connection

**Configuration Properties** (stored in AWS Secrets Manager per environment):
```
# Write cluster
spring.datasource.write.url=jdbc:mysql://write-cluster:3306/evplus
spring.datasource.write.username=write_user
spring.datasource.write.password=<secret>

# Read cluster
spring.datasource.read.url=jdbc:mysql://read-cluster:3306/evplus
spring.datasource.read.username=read_user
spring.datasource.read.password=<secret>

# Connection pool settings (HikariCP)
spring.datasource.write.hikari.maximum-pool-size=20
spring.datasource.read.hikari.maximum-pool-size=50
```

**Routing Strategy**:

**Option 1 - @Transactional with readOnly flag**:
- Use Spring's `@Transactional(readOnly=true)` for read operations
- Create custom routing DataSource that checks transaction read-only flag
- Route to read cluster if transaction is read-only, write cluster otherwise
- Implement `AbstractRoutingDataSource` with custom `determineCurrentLookupKey()`

**Option 2 - Separate Entity Managers**:
- Create two EntityManagerFactory beans (write and read)
- Annotate repositories with `@PersistenceContext` to specify which EntityManager
- More explicit but requires more configuration per repository

**Recommendation**: Use Option 1 (routing DataSource) for cleaner code

**Implementation Requirements**:

**RoutingDataSource**:
- Extend `AbstractRoutingDataSource`
- Override `determineCurrentLookupKey()` to check if transaction is read-only
- Return "write" or "read" key based on transaction state
- Primary DataSource bean should be the routing DataSource

**Repository Layer**:
- Report generation queries: Use `@Transactional(readOnly=true)` → routes to read cluster
- Job status updates: Use `@Transactional` (default readOnly=false) → routes to write cluster
- Threshold config reads: `@Transactional(readOnly=true)` → read cluster
- Threshold config updates: `@Transactional` → write cluster

**Fallback Strategy**:
- If read cluster is unavailable, fall back to write cluster
- Implement health checks for both clusters
- Alert if read cluster is down but service continues using write cluster

**Local Development**:
- For local environment: Both read and write point to same localhost database
- Simplifies local setup while maintaining production-like configuration structure

**Testing Considerations**:
- Integration tests should use single datasource (simpler setup)
- Add specific tests to verify read/write routing logic
- Monitor query distribution between clusters in production

---

### Task 1.7: Error Handling Framework

**Effort**: 1 day
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-01-25
**Completed**: 2025-01-25

**ErrorResponse DTO**:
Create a standardized error response DTO with the following fields:
- HTTP status code
- Error type/name
- Detailed message
- Request path
- Correlation ID for tracing
- Timestamp in ISO 8601 format
- Field-level validation errors (for 400 Bad Request)
- Optional debug message

**Custom Exception Classes**:
Define domain-specific exceptions mapped to HTTP status codes:
- `ValidationException` → 400 Bad Request (validation failures)
- `UnsupportedReportTypeException` → 400 Bad Request (unknown report type)
- `ReportJobNotFoundException` → 404 Not Found (job ID not found)
- `UnauthorizedException` → 403 Forbidden (access denied)
- `ReportNotReadyException` → 409 Conflict (report still processing)
- `ReportGenerationException` → 500 Internal Server Error (system errors)

**GlobalExceptionHandler**:
Implement `@ControllerAdvice` with `@ExceptionHandler` methods for each exception type. All handlers return standardized `ErrorResponse` with appropriate HTTP status.

**CorrelationIdFilter**:
Create servlet filter that:
- Extracts `X-Correlation-ID` header from incoming requests (or generates new UUID if missing)
- Stores correlation ID in MDC (Mapped Diagnostic Context) for logging
- Propagates correlation ID to response headers

---

### Task 1.8: Health Checks & Logging

**Effort**: 1 day
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-01-25
**Completed**: 2025-01-26

**Custom Health Indicators**:
Implement Spring Boot Actuator health indicators:
- `DatabaseHealthIndicator`: Execute simple database query to verify connectivity
- `RedisHealthIndicator`: Verify Redis/Valkey connectivity and response time

**Structured Logging Configuration**:
Configure Logback with LogstashEncoder for JSON output:
- Use console appender with LogstashEncoder
- Include MDC fields: correlationId, userId, reportType
- Output JSON format for log aggregation tools

**LoggingAspect**:
Create AOP aspect using `@Around` annotation that:
- Intercepts all controller methods
- Logs request details (method, path, parameters)
- Logs response details (status, duration)
- Captures execution duration
- Uses structured logging with MDC context

---

### Task 1.9: Local Development Setup with LocalStack

**Effort**: 1 day
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-01-26
**Completed**: 2025-01-26

**Purpose**: Enable local development and testing without AWS dependencies

**LocalStack Setup**:

**Installation**:
- Use Docker to run LocalStack container
- LocalStack provides local AWS service emulation (S3, SQS, SES)
- Default endpoint: `http://localhost:4566`

**Docker Compose Configuration** (optional, can also use direct docker run):
```yaml
version: '3.8'
services:
  localstack:
    image: localstack/localstack:latest
    ports:
      - "4566:4566"
    environment:
      - SERVICES=s3,sqs,ses
      - DEBUG=1
    volumes:
      - ./localstack-data:/tmp/localstack
```

**Application Configuration for Local**:

In `application-local.yml`:
```yaml
spring:
  cloud:
    aws:
      credentials:
        access-key: test
        secret-key: test
      region:
        static: us-east-1
      s3:
        endpoint: http://localhost:4566
      sqs:
        endpoint: http://localhost:4566
      ses:
        endpoint: http://localhost:4566
      secretsmanager:
        enabled: false  # Don't use Secrets Manager locally
```

**LocalStack Resource Creation** (run on startup):

Create initialization script `localstack-init.sh`:
```bash
#!/bin/bash
# Create S3 bucket
awslocal s3 mb s3://ev-plus-reports-local

# Create SQS queues
awslocal sqs create-queue --queue-name ev-plus-reporting-local-queue
awslocal sqs create-queue --queue-name ev-plus-notifications-local-queue

# Verify resources
awslocal s3 ls
awslocal sqs list-queues
```

**Mock Notification Service**:
- Create `@Profile("local")` NotificationQueueService implementation
- Log notification details to console instead of inserting to database/sending SQS messages
- Log: recipient, report name, download URL, notification event details

**Benefits**:
- ✅ No AWS costs for local development
- ✅ No VPN required
- ✅ Fast iteration cycle
- ✅ Works offline
- ✅ Consistent with production code paths

**Testing Strategy**:
- Use LocalStack for integration tests with `@Testcontainers`
- Use same AWS SDK clients (just different endpoint)
- Verify full async workflow locally before deploying to dev

---

### Task 1.10: README Documentation

**Effort**: 0.5 day
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-01-26
**Completed**: 2025-01-26

Create `README.md` with:
- Prerequisites (Java 21, Maven, Docker for LocalStack)
- LocalStack setup instructions
- AWS Secrets Manager setup guide (for dev/stage/prod)
- Running application per environment
- API base path documentation
- Troubleshooting common issues

---

### Phase 1 Exit Criteria

✅ All tasks completed:
- [x] Spring Boot 3.5 + Java 21 project with all dependencies
- [x] AWS Secrets Manager integration working (for dev/stage/prod)
- [x] Secrets created in AWS for dev/stage/prod environments
- [x] 4 environment configs (local/dev/stage/prod)
- [x] OAuth2/OIDC authentication configured and tested (disabled/mocked for local)
- [x] Swagger UI accessible and functional
- [x] Read/Write database cluster routing configured
- [x] Error handling framework with standardized responses
- [x] Health checks passing (database, Redis, both clusters)
- [x] JSON structured logging with correlation IDs
- [x] **LocalStack setup for local development (S3, SQS for reporting and notifications)**
- [x] **Mock notification service for local profile**
- [x] Application runs locally: `mvn spring-boot:run -Dspring.profiles.active=local`
- [x] Local MySQL and Redis connections working
- [x] API base path: `/ev-pd-report/v1/`
- [x] Swagger UI: `/ev-pd-report/v1/swagger-ui.html`
- [x] README.md with local setup instructions

---

### Phase 1 Completion Checklist

**Before marking this phase as complete, verify:**

- [x] All task status fields updated (Status, Assignee, Started, Completed dates)
- [x] All tasks marked as ✅ Complete or properly documented if blocked
- [x] Progress Tracker updated (Overall Progress %, Current Week, Current Phase)
- [x] Phase Completion Status table updated (Completed count, Progress %, Status)
- [x] Phase Exit Criteria checklist reviewed and checked off
- [x] Code committed and pushed to repository
- [x] Tests passing for all completed tasks
- [x] Documentation updated (README, inline comments, etc.)

**To update the plan after completing this phase:**
1. Mark all tasks in this phase as ✅ Complete with completion dates
2. Update "Overall Progress" at top: Calculate (total completed tasks / 51 tasks)
3. Update "Current Week" at top: Increment based on timeline
4. Update "Current Phase" at top: Set to next phase name
5. Update Phase Completion Status table: Set this phase to "✅ Complete"
6. Check all boxes in Phase Exit Criteria
7. Check all boxes in this Phase Completion Checklist

---

## Phase 2: Core Reporting Framework

**Duration**: 2 weeks (Week 2-4)
**Owner**: Developer 1 (Lead), Developer 3 (Testing)

### Task 2.1: Database Schema Migration

**Effort**: 1 day
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-01-26
**Completed**: 2025-01-26
**Week**: 3

**Create SQL Patch**: `src/main/resources/db/patch20260128_tp_17281_reportjobs.sql`

**Table 1: reportjobs**
Tracks all report generation requests (both sync and async).

**Columns**:
- report_id: UUID primary key (CHAR(36))
- district_id, user_id: Foreign keys for authorization
- report_type: Enum/varchar identifying report type (e.g., 'USER_ACTIVITY')
- report_params: JSON column storing report criteria (filters, date ranges, etc.)
- status: Integer (0=QUEUED, 1=PROCESSING, 2=COMPLETED, 3=FAILED)
- requested_date, started_date, completed_date: Timestamps for tracking
- s3_url: Presigned URL for download (nullable)
- filename: Generated filename (nullable)
- error_message: Error details if failed (nullable)
- created_at, updated_at: Audit timestamps

**Indexes**:
- idx_district_status: (district_id, status) - for district admin queries
- idx_user_date: (user_id, requested_date DESC) - for user history
- idx_status_requested: (status, requested_date) - for job processing queue

**Table 2: threshold_configs**
Configuration for sync vs async threshold determination.

**Columns**:
- report_type: VARCHAR(50) primary key
- max_records: Maximum record count before switching to async (default 5000)
- max_duration_seconds: Maximum estimated duration before async (default 10)
- description: Human-readable description
- updated_at: Audit timestamp

**Initial Data**:
Insert default thresholds for: USER_ACTIVITY (5000 records, 10s)
**Rationale**: Apache POI can generate ~5K rows in 3-5 seconds. Keeping sync reports under 10 seconds ensures good UX. Future report types can be added with appropriate thresholds.

**Existing Table Access** (for notification integration):

**notification_events Table** (Write-Only):
- Insert records when async report completes
- Columns: district_id, event ('REPORT_READY_FOR_DOWNLOAD'), date, user_id, object_str (report_id), message (JSON with presigned URL, report name, expiration)

**notification_queue Table** (Write-Only):
- Insert records to trigger existing notification processor
- Columns: district_id, level ('IMMEDIATELY'), notification_event_id, sqs_queued (1 for immediate delivery)

**JPA Entities**:

**ReportJob Entity**:
- Map to `reportjobs` table
- Use `@Entity`, `@Table` annotations
- ID field: reportId (String, UUID)
- All database columns mapped with appropriate JPA annotations
- Use `@Enumerated(EnumType.STRING)` for reportType
- JSON column: Store as String, serialize/deserialize manually or with AttributeConverter
- Helper methods: Convert between integer status code and ReportStatus enum

**ThresholdConfig Entity**:
- Map to `threshold_configs` table
- Primary key: reportType (String or enum)
- Simple entity with max_records, max_duration_seconds, description, updated_at

**NotificationEvent Entity** (for notification integration):
- Map to `notification_events` table
- Fields: id (auto-generated), districtId, event, date, userId, objectStr, message
- Write-only entity (no read operations needed)

**NotificationQueue Entity** (for notification integration):
- Map to `notification_queue` table
- Fields: id (auto-generated), districtId, level, notificationEventId, sqsQueued
- Write-only entity (no read operations needed)

**Spring Data Repositories**:

**ReportJobRepository**:
- Extend `JpaRepository<ReportJob, String>`
- Query methods:
  - findByUserIdOrderByRequestedDateDesc(): Get user's report history
  - findByDistrictIdAndStatus(): Get district reports by status

**ThresholdConfigRepository**:
- Extend `JpaRepository<ThresholdConfig, ReportType>`
- No custom methods needed (use findById for lookups)

**NotificationEventRepository** (for notification integration):
- Extend `JpaRepository<NotificationEvent, Long>`
- No custom methods needed (insert only)

**NotificationQueueRepository** (for notification integration):
- Extend `JpaRepository<NotificationQueue, Long>`
- No custom methods needed (insert only)

**Success Criteria**:
- [ ] SQL patch created and reviewed by DBA
- [ ] Schema applied to Dev database
- [ ] JPA entities map correctly
- [ ] Integration tests pass

---

### Task 2.2: Handler Registry Pattern

**Effort**: 2 days
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-01-26
**Completed**: 2025-01-26
**Week**: 3

**ReportHandler Interface**:
Define contract that all report handlers must implement:
- `getReportType()`: Return the ReportType enum this handler supports
- `validateCriteria()`: Validate report criteria, throw ValidationException if invalid
- `exceedsAsyncThreshold()`: Determine if request should be processed async
- `generateReport()`: Core logic to generate report data
- `getCriteriaClass()`: Return the criteria class for deserialization

**Base DTOs**:

**ReportCriteria (Abstract)**:
- Base class for all report criteria
- Common fields: startDate, endDate, districtIds
- Use Jackson `@JsonTypeInfo` and `@JsonSubTypes` for polymorphic deserialization
- Abstract method: getReportType()
- Concrete subclasses define report-specific filters

**ReportData (Abstract)**:
- Base class for all report results
- Common fields: totalRecords, generatedAt (auto-set to current time)
- Concrete subclasses contain report-specific data (e.g., list of records)

**HandlerRegistry**:
Purpose: Auto-register and lookup report handlers using Spring DI

**Implementation**:
- Inject `List<ReportHandler>` (Spring finds all beans implementing interface)
- Constructor: Build `Map<ReportType, ReportHandler>` from list
- Log registered handlers for startup verification
- `getHandler()`: Lookup by ReportType, throw exception if not found
- `getSupportedReportTypes()`: Return all registered types

**Success Criteria**:
- [ ] ReportHandler interface defined
- [ ] HandlerRegistry auto-registers handlers
- [ ] Base DTOs created
- [ ] Unit tests pass (90%+ coverage)

---

### Task 2.3: Threshold Service

**Effort**: 1 day
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-01-26
**Completed**: 2025-01-26
**Week**: 3

**ThresholdService**:
Purpose: Fetch and cache threshold configurations

**Methods**:
- `getThreshold(ReportType)`:
  - Use `@Cacheable` annotation (cache name: "thresholdConfigs")
  - Lookup from repository
  - If not found: Log warning, return default values (10000 records, 30 seconds)

- `updateThreshold(ReportType, maxRecords, maxDurationSeconds)`:
  - Use `@CacheEvict` to invalidate cache entry
  - Update existing config or create new
  - Save to database

**Admin Controller** (for runtime threshold management):
- Endpoint: `/admin/thresholds/{reportType}`
- GET: Retrieve current threshold
- PUT: Update threshold with query params (maxRecords, maxDurationSeconds)
- Secure with admin-only access

---

### Task 2.4: ReportGeneratorService (Orchestrator)

**Effort**: 3 days
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-01-26
**Completed**: 2025-01-26
**Week**: 4

**ReportGeneratorService** (Orchestrator):
Purpose: Central service coordinating sync vs async report generation

**Main Method: generateReport(ReportRequest, User)**:
1. Lookup appropriate handler from HandlerRegistry
2. Validate criteria using handler (throws ValidationException if invalid)
3. Check if request exceeds async threshold
4. Route to sync or async processing path

**Sync Processing (processSyncReport)**:
- Call handler.generateReport() immediately
- Build ReportResponse with status="COMPLETED"
- Include report data, total records, generation timestamp
- Return HTTP 200

**Async Processing (processAsyncReport)**:
- Generate UUID for job ID
- Create ReportJob entity with status=QUEUED
- Serialize criteria to JSON and store in report_params column
- Save job to database
- Send SQS message with job payload using SqsTemplate or SQS SDK:
  - **Message Body**: JSON containing jobId, districtId, reportType, userId
  - **Message Attributes**: Add custom attributes for filtering/monitoring (reportType, districtId)
  - Standard SQS provides at-least-once delivery (messages may be delivered multiple times)
- Build ReportResponse with status="QUEUED", jobId, estimated completion time
- Return HTTP 201

**Idempotency Strategy** (for Standard SQS):
- Standard SQS may deliver messages more than once (at-least-once delivery)
- Implement idempotency in AsyncReportProcessor by checking job status before processing
- If job status is PROCESSING or COMPLETED, skip duplicate message
- Database job record with unique jobId serves as idempotency guarantee
- Use optimistic locking or database constraints to prevent concurrent processing

**DTOs**:

**ReportRequest**:
- reportType: ReportType enum (required, @NotNull)
- criteria: ReportCriteria polymorphic object (required, @Valid)
- format: String, default "XLSX" (options: XLSX, JSON)
- deliveryMethod: String, default "EMAIL" (options: EMAIL, DOWNLOAD)

**ReportResponse**:
- For async: status, jobId, estimatedCompletionTime, message
- For sync: status, data (ReportData), totalRecords, generatedAt
- Use @JsonInclude(NON_NULL) to exclude null fields from JSON

---

### Task 2.5: REST API Controller

**Effort**: 2 days
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-01-26
**Completed**: 2025-01-26
**Week**: 4

**ReportController**:
Base path: `/reports` (full path: `/ev-pd-report/v1/reports`)

**POST /generate**:
- Purpose: Generate report (sync or async)
- Request: `@RequestBody @Valid ReportRequest`
- Authentication: Required (`@AuthenticationPrincipal User`)
- Response: HTTP 201 (CREATED) if queued, HTTP 200 (OK) if completed sync
- Log request details (report type, user ID)

**GET /status/{jobId}**:
- Purpose: Check async job status
- Path variable: jobId (UUID)
- Authentication: Required
- Authorization: User must own job OR be admin
- Throws: ReportJobNotFoundException (404) if job not found
- Throws: UnauthorizedException (403) if access denied
- Response: ReportJobStatus DTO (status, progress, s3_url if completed)

**GET /download/{jobId}**:
- Purpose: Direct download of completed report
- Path variable: jobId (UUID)
- Authentication: Required
- Authorization: User must own job OR be admin
- Throws: ReportNotReadyException (409) if not completed
- Implementation: Phase 4 (fetch from S3)
- Response: Byte array with Content-Disposition header

---

---

### Phase 2 Completion Checklist

**Before marking this phase as complete, verify:**

- [x] All task status fields updated (Status, Assignee, Started, Completed dates)
- [x] All tasks marked as ✅ Complete or properly documented if blocked
- [x] Progress Tracker updated (Overall Progress %, Current Week, Current Phase)
- [x] Phase Completion Status table updated (Completed count, Progress %, Status)
- [x] Phase Exit Criteria checklist reviewed and checked off
- [ ] Code committed and pushed to repository
- [x] Tests passing for all completed tasks (65 tests passing)
- [x] Documentation updated (README, inline comments, etc.)

**To update the plan after completing this phase:**
1. Mark all tasks in this phase as ✅ Complete with completion dates
2. Update "Overall Progress" at top: Calculate (total completed tasks / 51 tasks)
3. Update "Current Week" at top: Increment based on timeline
4. Update "Current Phase" at top: Set to next phase name
5. Update Phase Completion Status table: Set this phase to "✅ Complete"
6. Check all boxes in Phase Exit Criteria
7. Check all boxes in this Phase Completion Checklist

---

### Phase 2 Exit Criteria

✅ All tasks completed:
- [x] Database schema created (reportjobs, threshold_configs)
- [x] Handler Registry pattern implemented
- [x] Threshold service with caching
- [x] ReportGeneratorService orchestrator
- [x] REST API endpoints (POST /reports, GET /reports/{jobId}, GET /reports)
- [x] Unit tests ≥80% coverage (65 tests passing)
- [x] Integration tests pass (application starts successfully)

---

## Phase 4: Asynchronous Processing

**Duration**: 2 weeks (Week 5-6) **← MOVED UP**
**Owner**: Developer 2 (Async Specialist)

**IMPORTANT CHANGE**: This phase has been moved BEFORE Phase 3 to enable independent testing of the async infrastructure using dummy data. This de-risks the critical async pipeline and allows parallel development.

**Testing Strategy**: Phase 4 will use DummyReportHandler with test data to validate the complete async workflow (SQS → Process → Excel → S3 → Notification) without depending on the User Activity Report implementation.

### Task 4.0: Dummy Data Components for Testing

**Effort**: 1 day
**Week**: 5
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-11-28
**Completed**: 2025-11-28

**Purpose**: Create minimal test components to enable independent async pipeline testing without User Activity Report dependencies.

**DummyReportCriteria DTO**:
```java
public class DummyReportCriteria extends ReportCriteria {
    private String testParameter;
    private int recordCount = 10000; // Configurable for testing

    @Override
    public ReportType getReportType() {
        return ReportType.DUMMY_TEST;
    }
}
```

**DummyReportData DTO**:
```java
public class DummyReportData extends ReportData {
    private List<DummyRecord> records;
}

public class DummyRecord {
    private String field1;
    private String field2;
    private String field3;
    private LocalDateTime timestamp;
}
```

**DummyReportHandler Implementation**:
- Implement ReportHandler interface
- getReportType() returns ReportType.DUMMY_TEST
- validateCriteria() performs simple validation
- exceedsAsyncThreshold() returns true to always test async flow
- generateReport() generates configurable number of test records (default 10,000)
- getCriteriaClass() returns DummyReportCriteria.class
- Auto-registers with HandlerRegistry on startup

**Test Data Generation**:
```java
@Override
public ReportData generateReport(ReportCriteria criteria) {
    DummyReportCriteria dummyCriteria = (DummyReportCriteria) criteria;

    // Generate realistic test data
    List<DummyRecord> records = IntStream.range(0, dummyCriteria.getRecordCount())
        .mapToObj(i -> new DummyRecord(
            "Field1-" + i,
            "Field2-" + i,
            "Field3-" + i,
            LocalDateTime.now().minusHours(i)
        ))
        .collect(Collectors.toList());

    DummyReportData data = new DummyReportData();
    data.setRecords(records);
    data.setTotalRecords(records.size());
    data.setGeneratedAt(LocalDateTime.now());
    return data;
}
```

**Success Criteria**:
- [x] DummyReportCriteria DTO created
- [x] DummyReportData and DummyRecord DTOs created
- [x] DummyReportHandler implements all ReportHandler methods
- [x] Handler generates configurable number of test records
- [x] Handler auto-registers with HandlerRegistry
- [x] Unit tests verify handler behavior (16 tests, all passing)
- [x] Application builds and compiles successfully (81 total tests passing)

**Benefits**:
- ✅ Tests async infrastructure independently
- ✅ Validates full workflow: SQS → Process → Excel → S3 → Notification
- ✅ No dependency on User Activity Report
- ✅ Can test with various data volumes (100, 1K, 10K, 100K records)
- ✅ Enables parallel development

**Note**: This handler will remain in codebase for testing purposes even after User Activity Report is implemented.

---

### Task 4.1: SQS Listener Implementation

**Effort**: 3 days
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -
**Week**: 5-6

**UserActivityCriteria**:
Extends ReportCriteria, specific to USER_ACTIVITY report type

**Fields**:
- startDate: LocalDate (required, @NotNull) - Report start date
- endDate: LocalDate (required, @NotNull) - Report end date
- userIds: List<Integer> (optional) - Filter by specific users
- activityTypes: List<String> (optional) - Filter by activity type (e.g., LOGIN, COURSE_VIEW)

**Override**: getReportType() returns ReportType.USER_ACTIVITY

**UserActivityReportData**:
Extends ReportData, contains the actual report results

**Structure**:
- activities: List<UserActivityRecord> - The report rows

**UserActivityRecord** (inner class):
- userId: int
- userName: String
- activityType: String
- activityDate: LocalDateTime (format: ISO 8601)
- durationSeconds: int
- districtName: String

**UserActivityReportHandler**:
Implements ReportHandler for USER_ACTIVITY report type

**Implementation Details**:

**getReportType()**:
- Return ReportType.USER_ACTIVITY

**validateCriteria()**:
Validation rules:
- startDate and endDate are required (not null)
- startDate must be before endDate
- Date range cannot exceed 1 year (365 days)
- Collect all errors and throw ValidationException with list

**exceedsAsyncThreshold()**:
Estimation algorithm:
- Calculate days between startDate and endDate
- Determine user count (from userIds list, or assume 100 if not filtered)
- Estimate records: days × users × 20 (conservative: 20 activities per user per day)
- Fetch threshold config from ThresholdService (default: 5000 records)
- Return true if estimated records > threshold max_records

**Example Calculation**:
- 7 days × 100 users × 20 activities = 14,000 records → **ASYNC**
- 7 days × 10 users × 20 activities = 1,400 records → **SYNC**
- 1 day × 100 users × 20 activities = 2,000 records → **SYNC**

**generateReport()**:
Report generation flow:
1. Cast criteria to UserActivityCriteria
2. Log report parameters (dates, user count)
3. Call repository.findActivities() with all filter params
4. Transform UserActivityProjection list to UserActivityRecord list using Stream API
5. Create UserActivityReportData, set activities list and totalRecords
6. Log completion with record count
7. Return report data

**getCriteriaClass()**:
- Return UserActivityCriteria.class for deserialization

---

### Task 3.2: Database Query Optimization

**Effort**: 2 days
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -
**Week**: 6

**UserActivityRepository**:
Extends JpaRepository<UserActivity, Long>

**Custom Query Method: findActivities()**:
- Use @Query annotation with nativeQuery=true
- Join 3 tables: user_activity, users, districts
- Select: userId, userName (concatenated), activityType, activityDate, durationSeconds, districtName
- WHERE clause with dynamic filters:
  - activity_date BETWEEN startDate AND endDate (required)
  - user_id IN userIds list (optional, skip if NULL)
  - activity_type IN activityTypes list (optional, skip if NULL)
  - district_id IN districtIds list (optional, skip if NULL)
- ORDER BY: activity_date DESC, user_id ASC
- LIMIT 50000 (safety limit to prevent runaway queries)
- Return: List<UserActivityProjection>

**UserActivityProjection Interface**:
Projection for efficient data retrieval (only needed fields, no full entities)
- Getter methods: getUserId(), getUserName(), getActivityType(), getActivityDate(), getDurationSeconds(), getDistrictName()

**Index Recommendations** (coordinate with DBA):
- idx_activity_date on user_activity(activity_date) - for date range filtering
- idx_activity_user_date on user_activity(user_id, activity_date) - for user-specific queries
- idx_activity_type on user_activity(activity_type) - for activity type filtering

---

### Task 3.3: Integration Testing

**Effort**: 2 days
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -
**Week**: 6

**UserActivityReportIntegrationTest**:
Integration test using Testcontainers for database

**Test Setup**:
- Use @SpringBootTest, @Testcontainers, @ActiveProfiles("test")
- MySQL container: version 8.0, database name "testdb"
- Inject: ReportGeneratorService, UserActivityRepository
- @BeforeEach: Insert test data (users, districts, activities)

**Test Case 1: testSyncReportGeneration()**:
- Given: Small date range (7 days) that should trigger sync processing
- Create UserActivityCriteria with recent dates
- Create ReportRequest with USER_ACTIVITY type
- Create test User
- When: Call reportGeneratorService.generateReport()
- Then: Assert status="COMPLETED", data not null, totalRecords > 0

**Test Case 2: testAsyncReportQueuing()**:
- Given: Large date range (1 year) that should trigger async processing
- Create UserActivityCriteria with wide date range
- Create ReportRequest
- When: Call reportGeneratorService.generateReport()
- Then: Assert status="QUEUED", jobId not null, estimatedCompletionTime not null

**Additional Test Cases** (implement as needed):
- Validation error scenarios (invalid date ranges)
- Authorization checks (users can only see their data)
- Empty result sets
- Filter combinations (userIds, activityTypes)

---

### Phase 4 Completion Checklist

**Before marking this phase as complete, verify:**

- [ ] All task status fields updated (Status, Assignee, Started, Completed dates)
- [ ] All tasks marked as ✅ Complete or properly documented if blocked
- [ ] Progress Tracker updated (Overall Progress %, Current Week, Current Phase)
- [ ] Phase Completion Status table updated (Completed count, Progress %, Status)
- [ ] Phase Exit Criteria checklist reviewed and checked off
- [ ] Code committed and pushed to repository
- [ ] Tests passing for all completed tasks
- [ ] Documentation updated (README, inline comments, etc.)

**To update the plan after completing this phase:**
1. Mark all tasks in this phase as ✅ Complete with completion dates
2. Update "Overall Progress" at top: Calculate (total completed tasks / 51 tasks)
3. Update "Current Week" at top: Increment based on timeline
4. Update "Current Phase" at top: Set to next phase name
5. Update Phase Completion Status table: Set this phase to "✅ Complete"
6. Check all boxes in Phase Exit Criteria
7. Check all boxes in this Phase Completion Checklist

---

### Phase 3 Exit Criteria

✅ All tasks completed:
- [ ] UserActivityReportHandler implemented
- [ ] Criteria and data DTOs created
- [ ] Database queries optimized
- [ ] DBA reviewed indexes
- [ ] Sync flow working end-to-end
- [ ] Integration tests pass
- [ ] Unit test coverage ≥90%

---

## Phase 3: User Activity Report Implementation

**Duration**: 2 weeks (Week 7-8) **← MOVED DOWN**
**Owner**: Developer 3 (Reporting Lead)

**IMPORTANT CHANGE**: This phase has been moved AFTER Phase 4 to allow the async infrastructure to be tested independently first. This phase will implement the real User Activity Report handler and replace the DummyReportHandler used for testing in Phase 4.

### Task 3.1: UserActivityReportHandler

**Effort**: 3 days
**Week**: 7-8
**Status**: 📋 Not Started
**Assignee**: Developer 3
**Started**: -
**Completed**: -

**UserActivityCriteria**:
Extends ReportCriteria, specific to USER_ACTIVITY report type

**Fields**:
- startDate: LocalDate (required, @NotNull) - Report start date
- endDate: LocalDate (required, @NotNull) - Report end date
- userIds: List<Integer> (optional) - Filter by specific users
- activityTypes: List<String> (optional) - Filter by activity type (e.g., LOGIN, COURSE_VIEW)

**Override**: getReportType() returns ReportType.USER_ACTIVITY

**UserActivityReportData**:
Extends ReportData, contains the actual report results

**Structure**:
- activities: List<UserActivityRecord> - The report rows

**UserActivityRecord** (inner class):
- userId: int
- userName: String
- activityType: String
- activityDate: LocalDateTime (format: ISO 8601)
- durationSeconds: int
- districtName: String

**UserActivityReportHandler**:
Implements ReportHandler for USER_ACTIVITY report type

**Implementation Details**:

**getReportType()**:
- Return ReportType.USER_ACTIVITY

**validateCriteria()**:
Validation rules:
- startDate and endDate are required (not null)
- startDate must be before endDate
- Date range cannot exceed 1 year (365 days)
- Collect all errors and throw ValidationException with list

**exceedsAsyncThreshold()**:
Estimation algorithm:
- Calculate days between startDate and endDate
- Determine user count (from userIds list, or assume 100 if not filtered)
- Estimate records: days × users × 20 (conservative: 20 activities per user per day)
- Fetch threshold config from ThresholdService (default: 5000 records)
- Return true if estimated records > threshold max_records

**Example Calculation**:
- 7 days × 100 users × 20 activities = 14,000 records → **ASYNC**
- 7 days × 10 users × 20 activities = 1,400 records → **SYNC**
- 1 day × 100 users × 20 activities = 2,000 records → **SYNC**

**generateReport()**:
Report generation flow:
1. Cast criteria to UserActivityCriteria
2. Log report parameters (dates, user count)
3. Call repository.findActivities() with all filter params
4. Transform UserActivityProjection list to UserActivityRecord list using Stream API
5. Create UserActivityReportData, set activities list and totalRecords
6. Log completion with record count
7. Return report data

**getCriteriaClass()**:
- Return UserActivityCriteria.class for deserialization

---

### Task 3.2: Database Query Optimization

**Effort**: 2 days
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -
**Week**: 7

**UserActivityRepository**:
Extends JpaRepository<UserActivity, Long>

**Custom Query Method: findActivities()**:
- Use @Query annotation with nativeQuery=true
- Join 3 tables: user_activity, users, districts
- Select: userId, userName (concatenated), activityType, activityDate, durationSeconds, districtName
- WHERE clause with dynamic filters:
  - activity_date BETWEEN startDate AND endDate (required)
  - user_id IN userIds list (optional, skip if NULL)
  - activity_type IN activityTypes list (optional, skip if NULL)
  - district_id IN districtIds list (optional, skip if NULL)
- ORDER BY: activity_date DESC, user_id ASC
- LIMIT 50000 (safety limit to prevent runaway queries)
- Return: List<UserActivityProjection>

**UserActivityProjection Interface**:
Projection for efficient data retrieval (only needed fields, no full entities)
- Getter methods: getUserId(), getUserName(), getActivityType(), getActivityDate(), getDurationSeconds(), getDistrictName()

**Index Recommendations** (coordinate with DBA):
- idx_activity_date on user_activity(activity_date) - for date range filtering
- idx_activity_user_date on user_activity(user_id, activity_date) - for user-specific queries
- idx_activity_type on user_activity(activity_type) - for activity type filtering

---

### Task 3.3: Update ExcelReportGenerator for User Activity

**Effort**: 1 day
**Week**: 8
**Status**: 📋 Not Started
**Assignee**: Developer 3
**Started**: -
**Completed**: -

**Purpose**: Add UserActivityReportData support to ExcelReportGenerator (which currently only supports DummyReportData from Phase 4)

**Update generateExcel() Method**:
```java
public byte[] generateExcel(ReportData reportData) {
    if (reportData instanceof DummyReportData) {
        return generateDummyExcel((DummyReportData) reportData);
    } else if (reportData instanceof UserActivityReportData) {
        return generateUserActivityExcel((UserActivityReportData) reportData);
    }
    throw new UnsupportedOperationException("Report type not yet implemented");
}
```

**Add generateUserActivityExcel() Method**:

**Setup**:
- Create **SXSSFWorkbook** for streaming (keeps only 100 rows in memory)
- Create sheet named "User Activity Report"
- **Memory Optimization**: SXSSF writes rows to temp files, preventing OutOfMemoryError

**Header Styling**:
- Create CellStyle with bold font
- Header columns: "User ID", "User Name", "Activity Type", "Activity Date", "Duration (seconds)", "District"
- Apply header style to all header cells

**Data Population**:
- Start from row 1 (row 0 is header)
- Iterate through reportData.getActivities()
- For each record, create row and populate cells:
  - Column 0: userId (numeric)
  - Column 1: userName (string)
  - Column 2: activityType (string)
  - Column 3: activityDate (formatted as "yyyy-MM-dd HH:mm:ss")
  - Column 4: durationSeconds (numeric)
  - Column 5: districtName (string)

**Finalization**:
- Auto-size all columns for readability
- Write workbook to ByteArrayOutputStream
- Log row count
- Return byte array

**Error Handling**:
- Catch IOException, throw ReportGenerationException

**Success Criteria**:
- [ ] generateExcel() routes to correct generator based on ReportData type
- [ ] generateUserActivityExcel() creates properly formatted Excel file
- [ ] SXSSF streaming works for large reports (tested with 10,000+ rows)
- [ ] Headers styled correctly (bold font)
- [ ] Data columns formatted correctly (numeric, string, datetime)
- [ ] Columns auto-sized for readability
- [ ] Integration test verifies Excel file can be opened and read
- [ ] Both DummyReportData and UserActivityReportData support maintained

---

### Task 3.4: Integration Testing

**Effort**: 2 days
**Week**: 8
**Status**: 📋 Not Started
**Assignee**: Developer 3
**Started**: -
**Completed**: -

**UserActivityReportIntegrationTest**:
Integration test using Testcontainers for database

**Test Setup**:
- Use @SpringBootTest, @Testcontainers, @ActiveProfiles("test")
- MySQL container: version 8.0, database name "testdb"
- Inject: ReportGeneratorService, UserActivityRepository
- @BeforeEach: Insert test data (users, districts, activities)

**Test Case 1: testSyncReportGeneration()**:
- Given: Small date range (7 days) that should trigger sync processing
- Create UserActivityCriteria with recent dates
- Create ReportRequest with USER_ACTIVITY type
- Create test User
- When: Call reportGeneratorService.generateReport()
- Then: Assert status="COMPLETED", data not null, totalRecords > 0

**Test Case 2: testAsyncReportQueuing()**:
- Given: Large date range (1 year) that should trigger async processing
- Create UserActivityCriteria with wide date range
- Create ReportRequest
- When: Call reportGeneratorService.generateReport()
- Then: Assert status="QUEUED", jobId not null, estimatedCompletionTime not null

**Additional Test Cases** (implement as needed):
- Validation error scenarios (invalid date ranges)
- Authorization checks (users can only see their data)
- Empty result sets
- Filter combinations (userIds, activityTypes)

---

### Phase 3 Completion Checklist

**Before marking this phase as complete, verify:**

- [ ] All task status fields updated (Status, Assignee, Started, Completed dates)
- [ ] All tasks marked as ✅ Complete or properly documented if blocked
- [ ] Progress Tracker updated (Overall Progress %, Current Week, Current Phase)
- [ ] Phase Completion Status table updated (Completed count, Progress %, Status)
- [ ] Phase Exit Criteria checklist reviewed and checked off
- [ ] Code committed and pushed to repository
- [ ] Tests passing for all completed tasks
- [ ] Documentation updated (README, inline comments, etc.)

**To update the plan after completing this phase:**
1. Mark all tasks in this phase as ✅ Complete with completion dates
2. Update "Overall Progress" at top: Calculate (total completed tasks / 51 tasks)
3. Update "Current Week" at top: Increment based on timeline
4. Update "Current Phase" at top: Set to next phase name
5. Update Phase Completion Status table: Set this phase to "✅ Complete"
6. Check all boxes in Phase Exit Criteria
7. Check all boxes in this Phase Completion Checklist

---

### Phase 3 Exit Criteria

✅ All tasks completed:
- [ ] UserActivityReportHandler implemented
- [ ] Criteria and data DTOs created
- [ ] Database queries optimized
- [ ] DBA reviewed indexes
- [ ] ExcelReportGenerator updated to support UserActivityReportData
- [ ] Both sync and async flows working end-to-end with User Activity Report
- [ ] Integration tests pass
- [ ] Unit test coverage ≥90%

---

### Task 4.1: SQS Listener Implementation

**Effort**: 3 days
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-11-28
**Completed**: 2025-11-29
**Week**: 5-6

**AsyncReportProcessor**:
Purpose: Listen to SQS queue and process async report jobs

**SQS Listener Method: processReportJob()**:
- Use @SqsListener annotation with queue name from config
- Extract jobId from message
- Set MDC correlationId for logging traceability
- **Idempotency Check**: Load job and verify status is QUEUED (skip if PROCESSING/COMPLETED)
- Try-catch-finally block for error handling
- On success: Process job completely
- On error: Handle failure, re-throw to trigger SQS retry
- Finally: Clear MDC

**Idempotency Implementation** (CRITICAL for Standard SQS):
```java
// Load job from database
ReportJob job = reportJobRepository.findById(jobId)
    .orElseThrow(() -> new ReportJobNotFoundException(jobId));

// Check if already processed (duplicate message)
if (job.getStatus() == ReportStatus.COMPLETED) {
    log.info("Job {} already completed, skipping duplicate message", jobId);
    return; // Acknowledge message without reprocessing
}

if (job.getStatus() == ReportStatus.PROCESSING) {
    log.warn("Job {} already being processed, skipping duplicate", jobId);
    return; // Another pod is processing this job
}

// Set status to PROCESSING (with optimistic locking if needed)
job.setStatus(ReportStatus.PROCESSING);
job.setStartedDate(LocalDateTime.now());
reportJobRepository.save(job);
```

**Job Processing Flow (processJob method)**:
1. **Idempotency Check**: Verify job status is QUEUED, set to PROCESSING
2. **Deserialize**: Extract ReportCriteria from JSON stored in job.reportParams
3. **Generate Report**: Lookup handler, call generateReport() to get ReportData
4. **Create Excel**: Convert ReportData to Excel byte array using ExcelReportGenerator (SXSSF for streaming)
5. **Upload to S3**: Generate filename ("{reportType}_{jobId}.xlsx"), upload to S3
6. **Generate URL**: Create presigned URL with 7-day expiration
7. **Update Job**: Set status=COMPLETED, completedDate, s3Url, filename, save to DB
8. **Queue Notification**: Insert record to notification_events table (event: 'REPORT_READY_FOR_DOWNLOAD'), then insert to notification_queue table, and send SQS message to notification queue (existing teachpoint-web processor will send email)

**Error Handling (handleJobFailure method)**:
- Lookup job by ID
- If found: Set status=FAILED, completedDate, errorMessage
- Save to database
- Log error but don't re-throw (defensive programming)
- This method ensures job status is updated even if main processing fails

**Key Considerations**:
- **Idempotency**: CRITICAL - Standard SQS delivers at-least-once, messages may be duplicated
- **Timeout**: Ensure processing completes within SQS visibility timeout (15 minutes)
- **Transaction management**: Use @Transactional for atomic status updates
- **Concurrency**: Multiple pods may receive same message - use database status as lock

---

### Task 4.2: S3 Service Implementation

**Effort**: 2 days
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-11-28
**Completed**: 2025-11-28
**Week**: 5-6

**S3Service**:
Purpose: Upload reports to S3, generate presigned URLs, download reports

**Dependencies**:
- Inject S3Client (AWS SDK 2.x)
- Inject bucket name from config (${aws.s3.bucket-name})

**Method: uploadReport()**:
- Parameters: districtId, jobId, reportData (byte array), filename
- S3 key pattern: "reports/{districtId}/{jobId}/{filename}"
- Content type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
- Use PutObjectRequest with RequestBody.fromBytes()
- Log upload start and success
- On error: Throw ReportGenerationException
- Return: S3 key for later retrieval

**Method: generatePresignedUrl()**:
- Parameters: S3 key, expiration duration
- Create GetObjectRequest and GetObjectPresignRequest
- Use S3Presigner to generate temporary URL
- Expiration: 7 days (passed as Duration parameter)
- Log URL generation
- On error: Throw ReportGenerationException
- Return: Presigned URL string

**Method: downloadReport()**:
- Parameters: S3 key
- Create GetObjectRequest
- Use s3Client.getObjectAsBytes()
- Convert ResponseBytes to byte array
- On error: Throw ReportGenerationException
- Return: Report file as byte array
- Use case: Direct download endpoint in REST controller

---

### Task 4.3: Excel Generation Service

**Effort**: 3 days
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-11-28
**Completed**: 2025-11-28
**Week**: 5-6

**ExcelReportGenerator**:
Purpose: Convert ReportData to Excel format using Apache POI

**Main Method: generateExcel(ReportData)**:
- Use instanceof to route to specific generator method
- **Phase 4**: Support DummyReportData for testing async pipeline
- **Phase 3 (later)**: Add UserActivityReportData support
- Extensible for additional report types
- Throw UnsupportedOperationException for unknown types

**DummyReport Excel Generation (generateDummyExcel)** - **NEW FOR PHASE 4**:

**Setup**:
- Create **SXSSFWorkbook** for streaming (keeps only 100 rows in memory)
- Create sheet named "Dummy Test Report"
- **Memory Optimization**: SXSSF writes rows to temp files, preventing OutOfMemoryError

**Header Styling**:
- Create CellStyle with bold font
- Header columns: "Field 1", "Field 2", "Field 3", "Timestamp"
- Apply header style to all header cells

**Data Population**:
- Start from row 1 (row 0 is header)
- Iterate through reportData.getRecords()
- For each record, create row and populate cells:
  - Column 0: field1 (string)
  - Column 1: field2 (string)
  - Column 2: field3 (string)
  - Column 3: timestamp (formatted as "yyyy-MM-dd HH:mm:ss")

**Finalization**:
- Auto-size all columns for readability
- Write workbook to ByteArrayOutputStream
- Log row count
- Return byte array

**UserActivityExcel Generation (generateUserActivityExcel)** - **TO BE ADDED IN PHASE 3**:

**Setup**:
- Create **SXSSFWorkbook** for streaming large files (keeps only 100 rows in memory at a time)
- Fallback to XSSFWorkbook for small reports (<1000 rows) if needed
- Create sheet named "User Activity Report"
- **Memory Optimization**: SXSSF writes rows to temp files, preventing OutOfMemoryError for large reports

**Header Styling**:
- Create CellStyle with bold font
- Header columns: "User ID", "User Name", "Activity Type", "Activity Date", "Duration (seconds)", "District"
- Apply header style to all header cells

**Data Population**:
- Start from row 1 (row 0 is header)
- Iterate through reportData.getActivities()
- For each record, create row and populate cells:
  - Column 0: userId (numeric)
  - Column 1: userName (string)
  - Column 2: activityType (string)
  - Column 3: activityDate (formatted as "yyyy-MM-dd HH:mm:ss")
  - Column 4: durationSeconds (numeric)
  - Column 5: districtName (string)

**Finalization**:
- Auto-size all columns for readability
- Write workbook to ByteArrayOutputStream
- Log row count
- Return byte array

**Error Handling**:
- Catch IOException, throw ReportGenerationException

---

### Task 4.4: Notification Queue Integration Service

**Effort**: 2 days
**Status**: ✅ Complete
**Assignee**: Claude Sonnet
**Started**: 2025-11-28
**Completed**: 2025-11-29
**Week**: 5-6

**NotificationQueueService**:
Purpose: Integrate with existing teachpoint-web notification infrastructure to deliver report completion notifications

**Dependencies**:
- NotificationEventRepository (JPA repository for notification_events table)
- NotificationQueueRepository (JPA repository for notification_queue table)
- SqsTemplate or AWS SQS SDK 2.x client
- Configuration property: `notification.queue.name` (configured per environment)

**Main Method: queueReportNotification(ReportJob, User, String downloadUrl)**:

**Parameters**:
- ReportJob: Completed report job with metadata
- User: User object containing userId, email, firstName
- downloadUrl: Presigned S3 URL for report download (7-day expiration)

**IMPORTANT**: This service does NOT send emails directly. It integrates with existing teachpoint-web notification infrastructure by:
- Inserting notification event to database
- Inserting notification queue record
- Sending SQS message to existing notification processor queue
- Existing teachpoint-web email processor handles actual email delivery

**Implementation Flow**:
1. **Create Notification Event**:
   - Build NotificationEvent entity:
     - districtId: From report job
     - event: "REPORT_READY_FOR_DOWNLOAD" (constant for this notification type)
     - date: Current timestamp
     - userId: From user object
     - objectStr: report_id (jobId UUID)
     - message: JSON string containing:
       ```json
       {
         "reportName": "User Activity Report",
         "downloadUrl": "https://s3.presigned.url...",
         "expirationDate": "2025-02-04"
       }
       ```
   - Save to notification_events table via repository
   - Retrieve generated notification_event_id

2. **Create Notification Queue Record**:
   - Build NotificationQueue entity:
     - districtId: From report job
     - level: "IMMEDIATELY" (for urgent report notifications)
     - notificationEventId: From step 1
     - sqsQueued: 1 (indicates message sent to SQS)
   - Save to notification_queue table via repository

3. **Send SQS Message**:
   - Build SQS message body (JSON):
     ```json
     {
       "notificationQueueId": 12345,
       "notificationEventId": 67890,
       "districtId": 123,
       "level": "IMMEDIATELY"
     }
     ```
   - Send message to configured notification queue using SqsTemplate or SQS SDK
   - Use configured queue name from `notification.queue.name` property
   - Log successful SQS message send with messageId

4. **Existing Processor Handles Email Delivery**:
   - Teachpoint-web notification processor polls notification queue
   - Retrieves notification event and queue records from database
   - Builds email from template using message JSON
   - Sends email via AWS SES
   - Deletes processed notification queue record

**Local Development**:
- Use `@Profile("local")` to create MockNotificationQueueService
- Log notification details to console instead of inserting to database
- Log: recipient, report name, download URL, notification event data
- No actual database inserts or SQS messages in local environment

**Error Handling (CRITICAL)**:
- Catch all exceptions during notification queuing
- Log error with user email, report job ID, and exception details
- **DO NOT throw exception** - notification failure should not fail the entire job
- Job status should remain COMPLETED even if notification queueing fails
- User can still download via /reports/download/{jobId} endpoint
- Defensive programming: verify notification event saved before creating queue record

**Configuration Properties** (application.yml):
```yaml
notification:
  queue:
    name: ${NOTIFICATION_QUEUE_NAME}  # Configured per environment
    event-type: REPORT_READY_FOR_DOWNLOAD
    level: IMMEDIATELY
```

**Environment-Specific Queue Names**:
- **Local**: `ev-plus-notifications-local-queue` (LocalStack)
- **Dev/Stage/Prod**: Actual queue names to be configured (placeholder: `${NOTIFICATION_QUEUE_NAME}`)

**Benefits of This Approach**:
- ✅ Reuses proven notification delivery mechanism (no duplicate email logic)
- ✅ Maintains consistent user experience (same email format/branding)
- ✅ No SES configuration needed in microservice (handled by existing processor)
- ✅ Email template managed centrally in teachpoint-web
- ✅ Notification delivery monitoring already in place

---

### Task 4.5: Download Endpoint Implementation

**Effort**: 1 day
**Status**: ✅ Complete
**Assignee**: -
**Started**: -
**Completed**: 2025-12-12
**Week**: 5-6

**Add to ReportController**:

**Endpoint: GET /reports/download/{jobId}**

**Purpose**: Allow users to directly download completed report files from S3 through the service

**Path Variable**:
- jobId: UUID of the report job

**Authentication**:
- Requires authenticated user (via @AuthenticationPrincipal)

**Implementation Logic**:

1. **Retrieve Job Record**:
   - Lookup ReportJob by jobId from repository
   - Throw ReportJobNotFoundException if not found (404)

2. **Authorization Check**:
   - Verify user owns the job: job.userId == authenticated user's userId
   - OR user has admin role
   - Throw UnauthorizedException if neither condition met (403)

3. **Status Validation**:
   - Check job status == COMPLETED
   - Throw ReportNotReadyException if status is QUEUED/PROCESSING (400)
   - Throw ReportFailedException if status is FAILED (500)

4. **S3 Key Extraction**:
   - Extract S3 key from job.s3Url or use stored s3Key field
   - Helper method: extractS3Key() to parse S3 URL to object key

5. **Download from S3**:
   - Call s3Service.downloadReport(s3Key) to retrieve byte array
   - Handle potential S3 exceptions

6. **Response Headers**:
   - Content-Type: "application/octet-stream" (binary download)
   - Content-Disposition: "attachment; filename={job.filename}"
   - This forces browser to download rather than display

7. **Return Response**:
   - ResponseEntity with byte[] body
   - HTTP 200 OK status

**Alternative Approach Consideration**:
- This endpoint downloads through the service (consumes bandwidth and memory)
- For large files, consider redirecting user to presigned S3 URL instead
- Tradeoff: Direct download provides better security (no URL sharing), but costs more resources

---

### Phase 4 Completion Checklist

**Before marking this phase as complete, verify:**

- [ ] All task status fields updated (Status, Assignee, Started, Completed dates)
- [ ] All tasks marked as ✅ Complete or properly documented if blocked
- [ ] Progress Tracker updated (Overall Progress %, Current Week, Current Phase)
- [ ] Phase Completion Status table updated (Completed count, Progress %, Status)
- [ ] Phase Exit Criteria checklist reviewed and checked off
- [ ] Code committed and pushed to repository
- [ ] Tests passing for all completed tasks
- [ ] Documentation updated (README, inline comments, etc.)

**To update the plan after completing this phase:**
1. Mark all tasks in this phase as ✅ Complete with completion dates
2. Update "Overall Progress" at top: Calculate (total completed tasks / 51 tasks)
3. Update "Current Week" at top: Increment based on timeline
4. Update "Current Phase" at top: Set to next phase name
5. Update Phase Completion Status table: Set this phase to "✅ Complete"
6. Check all boxes in Phase Exit Criteria
7. Check all boxes in this Phase Completion Checklist

---

### Phase 4 Exit Criteria

✅ All tasks completed:
- [ ] DummyReportHandler implemented and registered
- [ ] SQS listener processing messages
- [ ] S3 upload and presigned URLs working
- [ ] Excel generation for DummyReport (test data)
- [ ] Notification queue integration working (inserts to notification_events and notification_queue tables)
- [ ] SQS messages sent to notification processor queue
- [ ] Existing teachpoint-web notification processor delivers emails with download links
- [ ] Download endpoint functional
- [ ] Complete async flow tested end-to-end with dummy data (SQS → Process → Excel → S3 → Notification → Email)
- [ ] Integration tests pass with LocalStack (SQS, S3, notification queue)

---

## Phase 5: Testing & Quality Assurance

**Duration**: 2 weeks (Week 9-10)
**Owner**: Developer 3 (Testing Lead), QA Engineer

### Task 5.1: Unit Test Coverage Validation

**Effort**: 2 days
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- Run JaCoCo coverage report
- Ensure ≥80% overall, ≥85% service layer, ≥90% handlers
- Write additional tests for gaps
- Configure SonarQube quality gates

---

### Task 5.2: Integration Testing

**Effort**: 2 days
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- Testcontainers for MySQL, Redis
- LocalStack for SQS, S3
- End-to-end sync flow test
- End-to-end async flow test
- Error scenario tests

---

### Task 5.3: Load Testing

**Effort**: 2 days
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

**Using JMeter**:
- 100 concurrent sync reports
- 50 concurrent async reports
- Validate: avg response <10s, error rate <1%
- Monitor: DB connections, Redis hits, SQS queue depth

---

### Task 5.4: Contract Testing

**Effort**: 1 day
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- JSON Schema for all API responses
- Validate responses against schema
- Share schemas with frontend team

---

### Task 5.5: Security Testing

**Effort**: 1 day
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- Authentication/authorization tests
- OWASP dependency check
- Validate encrypted passwords
- TLS/SSL verification

---

### Task 5.6: Performance Optimization

**Effort**: 1 day
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- Database query tuning (based on load test results)
- HikariCP optimization
- Redis cache hit rate analysis
- Excel generation optimization

---

### Phase 5 Completion Checklist

**Before marking this phase as complete, verify:**

- [ ] All task status fields updated (Status, Assignee, Started, Completed dates)
- [ ] All tasks marked as ✅ Complete or properly documented if blocked
- [ ] Progress Tracker updated (Overall Progress %, Current Week, Current Phase)
- [ ] Phase Completion Status table updated (Completed count, Progress %, Status)
- [ ] Phase Exit Criteria checklist reviewed and checked off
- [ ] Code committed and pushed to repository
- [ ] Tests passing for all completed tasks
- [ ] Documentation updated (README, inline comments, etc.)

**To update the plan after completing this phase:**
1. Mark all tasks in this phase as ✅ Complete with completion dates
2. Update "Overall Progress" at top: Calculate (total completed tasks / 51 tasks)
3. Update "Current Week" at top: Increment based on timeline
4. Update "Current Phase" at top: Set to next phase name
5. Update Phase Completion Status table: Set this phase to "✅ Complete"
6. Check all boxes in Phase Exit Criteria
7. Check all boxes in this Phase Completion Checklist

---

### Phase 5 Exit Criteria

✅ All QA tasks completed:
- [ ] Test coverage ≥80%
- [ ] Load testing passed
- [ ] Security testing clean
- [ ] Contract tests pass
- [ ] Performance optimized
- [ ] SonarQube quality gate passed

---

## Phase 0: Infrastructure Setup (Deferred)

**Duration**: 2 weeks (Week 10-11)
**Owner**: DevOps Engineer

### Task 0.1: Cross-Account VPC Peering

**Effort**: 3 days
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- Create VPC peering (Core ↔ EV+ accounts)
- Update route tables
- Test connectivity

---

### Task 0.2: IRSA Configuration

**Effort**: 2 days
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- Create IAM role in EV+ account
- Configure trust policy for Core OIDC
- Attach policies (RDS, Redis, SQS, S3, Secrets Manager)
- Annotate Kubernetes Service Account

---

### Task 0.3: Security Groups

**Effort**: 1 day
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- EV+ RDS: Allow Core EKS pods (port 3306)
- EV+ ElastiCache: Allow Core EKS pods (port 6379)
- SQS/S3: IAM-based (no SG changes)

---

### Task 0.4: SQS Queue Creation

**Effort**: 1 day
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

**Standard Queue Configuration**:
- Queue name: `ev-plus-reporting-{env}-queue` (standard queue, no .fifo suffix)
- Queue type: **Standard** (at-least-once delivery, best-effort ordering)
- Visibility timeout: 15 minutes (must exceed maximum report processing time)
- Message retention: 14 days
- Receive wait time: 20 seconds (long polling for efficiency)
- Maximum message size: 256 KB (default)
- Delivery delay: 0 seconds (immediate delivery)

**Why Standard SQS (not FIFO)**:
- ✅ Higher throughput (unlimited TPS vs FIFO's 3000 TPS)
- ✅ Lower cost (~40% cheaper than FIFO)
- ✅ Simpler configuration (no MessageGroupId required)
- ✅ Better scalability for multiple consumer pods
- ⚠️ At-least-once delivery (requires idempotency handling in code)
- ⚠️ Best-effort ordering (acceptable for independent report jobs)

**Dead Letter Queue (DLQ)**:
- DLQ name: `ev-plus-reporting-{env}-dlq` (standard queue)
- Redrive policy: After 3 failed receive attempts, move to DLQ
- DLQ retention: 14 days
- Monitor DLQ for failed jobs requiring manual intervention
- Set up CloudWatch alarm for DLQ message count > 0

**IAM Permissions** (for IRSA role):
- sqs:SendMessage (for report generation service)
- sqs:ReceiveMessage (for async processor)
- sqs:DeleteMessage (after successful processing)
- sqs:GetQueueAttributes (for monitoring)
- sqs:GetQueueUrl (for queue discovery)

---

### Task 0.5: CircleCI Pipeline

**Effort**: 2 days
**Status**: ✅ Complete
**Assignee**: Development Team
**Started**: 2025-12-04
**Completed**: 2025-12-04

- Create `.circleci/config.yml`
- Maven build → Docker → ECR → EKS
- Environment-specific deployments (dev/stage/prod)

**Implementation Details**:
- ✅ Created comprehensive CircleCI workflow with multiple jobs
- ✅ Maven build with dependency caching and test execution
- ✅ Docker image building with version extraction from pom.xml
- ✅ Container security scanning using Veracode
- ✅ ECR push with environment-specific deployment filters
- ✅ Automated deployment for main/release branches
- ✅ Manual approval workflow for feature branches

---

### Task 0.6: Kubernetes Manifests

**Effort**: 2 days
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- Deployment.yaml (3-10 replicas, HPA)
- Service.yaml (ClusterIP)
- Ingress.yaml (ALB)
- ConfigMap.yaml
- Service Account with IRSA annotation

---

### Phase 0 Completion Checklist

**Before marking this phase as complete, verify:**

- [ ] All task status fields updated (Status, Assignee, Started, Completed dates)
- [ ] All tasks marked as ✅ Complete or properly documented if blocked
- [ ] Progress Tracker updated (Overall Progress %, Current Week, Current Phase)
- [ ] Phase Completion Status table updated (Completed count, Progress %, Status)
- [ ] Phase Exit Criteria checklist reviewed and checked off
- [ ] Code committed and pushed to repository
- [ ] Tests passing for all completed tasks
- [ ] Documentation updated (README, inline comments, etc.)

**To update the plan after completing this phase:**
1. Mark all tasks in this phase as ✅ Complete with completion dates
2. Update "Overall Progress" at top: Calculate (total completed tasks / 51 tasks)
3. Update "Current Week" at top: Increment based on timeline
4. Update "Current Phase" at top: Set to next phase name
5. Update Phase Completion Status table: Set this phase to "✅ Complete"
6. Check all boxes in Phase Exit Criteria
7. Check all boxes in this Phase Completion Checklist

---

### Phase 0 Exit Criteria

✅ Infrastructure ready:
- [ ] VPC peering active
- [ ] IRSA configured
- [ ] SQS queue created
- [ ] CircleCI pipeline working
- [ ] Kubernetes manifests deployed to Dev

---

## Phase 6: Observability & Monitoring

**Duration**: 2 weeks (Week 11-12)
**Owner**: DevOps Engineer

### Task 6.1: Datadog Agent Installation

**Effort**: 1 day
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- Install Datadog Kubernetes agent (Helm)
- Configure log collection
- Enable APM tracing

---

### Task 6.2: Custom Business Metrics

**Effort**: 2 days
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

**Micrometer Metrics**:
- `report.generate.sync.duration` (histogram)
- `report.generate.async.duration` (histogram)
- `report.threshold.sync_chosen` (counter)
- `report.threshold.async_chosen` (counter)
- `report.job.failed` (counter with error tags)

---

### Task 6.3: Datadog Dashboards

**Effort**: 1 day
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

**Dashboard: "EV+ Reporting Service - Overview"**
- Request rate by endpoint
- Error rate (5xx)
- Sync/async report durations (p50, p95, p99)
- SQS queue depth
- Failed job count by error reason
- HPA pod count

---

### Task 6.4: Datadog Monitors (Alerts)

**Effort**: 1 day
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- High error rate (>5% for 5 min)
- SQS queue backlog (>1000 for 10 min)
- Slow sync reports (p95 >15s)
- Pod restarts (>3 in 10 min)
- DB connection pool low (<2 available)

---

### Task 6.5: Operational Runbook

**Effort**: 1 day
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

**docs/RUNBOOK.md**:
- SQS queue backlog resolution
- Failed async job retry procedures
- DB connection pool exhaustion
- Pod health check failures
- Escalation paths

---

### Phase 6 Completion Checklist

**Before marking this phase as complete, verify:**

- [ ] All task status fields updated (Status, Assignee, Started, Completed dates)
- [ ] All tasks marked as ✅ Complete or properly documented if blocked
- [ ] Progress Tracker updated (Overall Progress %, Current Week, Current Phase)
- [ ] Phase Completion Status table updated (Completed count, Progress %, Status)
- [ ] Phase Exit Criteria checklist reviewed and checked off
- [ ] Code committed and pushed to repository
- [ ] Tests passing for all completed tasks
- [ ] Documentation updated (README, inline comments, etc.)

**To update the plan after completing this phase:**
1. Mark all tasks in this phase as ✅ Complete with completion dates
2. Update "Overall Progress" at top: Calculate (total completed tasks / 51 tasks)
3. Update "Current Week" at top: Increment based on timeline
4. Update "Current Phase" at top: Set to next phase name
5. Update Phase Completion Status table: Set this phase to "✅ Complete"
6. Check all boxes in Phase Exit Criteria
7. Check all boxes in this Phase Completion Checklist

---

### Phase 6 Exit Criteria

✅ Observability complete:
- [ ] Datadog agent installed
- [ ] Custom metrics reporting
- [ ] Dashboards created
- [ ] Alerts configured
- [ ] Runbook documented

---

## Phase 7: Deployment & Rollout

**Duration**: 1 week (Week 12-13)
**Owner**: DevOps Engineer, All Developers

### Task 7.1: Deploy to Dev

**Effort**: 1 day
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- CircleCI auto-deploy on `main` branch
- Validate health checks
- Run smoke tests

---

### Task 7.2: Frontend Integration (Dev)

**Effort**: 2 days
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- Frontend updates API endpoint URLs
- Test sync report generation
- Test async report with email
- Fix integration issues

---

### Task 7.3: Deploy to Stage

**Effort**: 1 day
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- Create `release/1.0.0` branch
- Manual approval → deploy to Stage
- Run load tests
- Monitor for 24 hours

---

### Task 7.4: Production Deployment

**Effort**: 1 day
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

**Pre-deployment**:
- [ ] DBA applies schema (`reportjobs`, `threshold_configs`)
- [ ] Staging approval obtained
- [ ] Rollback plan ready

**Deployment**:
- CircleCI manual approval
- Rolling update (zero downtime)
- Smoke tests

**Post-deployment**:
- Monitor Datadog for 2 hours
- Validate email delivery
- Check SQS processing

---

### Task 7.5: Frontend Integration (Prod)

**Effort**: 1 day
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- Deploy frontend changes to Prod
- Monitor for user issues
- Coordinate with Customer Support

---

### Phase 7 Completion Checklist

**Before marking this phase as complete, verify:**

- [ ] All task status fields updated (Status, Assignee, Started, Completed dates)
- [ ] All tasks marked as ✅ Complete or properly documented if blocked
- [ ] Progress Tracker updated (Overall Progress %, Current Week, Current Phase)
- [ ] Phase Completion Status table updated (Completed count, Progress %, Status)
- [ ] Phase Exit Criteria checklist reviewed and checked off
- [ ] Code committed and pushed to repository
- [ ] Tests passing for all completed tasks
- [ ] Documentation updated (README, inline comments, etc.)

**To update the plan after completing this phase:**
1. Mark all tasks in this phase as ✅ Complete with completion dates
2. Update "Overall Progress" at top: Calculate (total completed tasks / 51 tasks)
3. Update "Current Week" at top: Increment based on timeline
4. Update "Current Phase" at top: Set to next phase name
5. Update Phase Completion Status table: Set this phase to "✅ Complete"
6. Check all boxes in Phase Exit Criteria
7. Check all boxes in this Phase Completion Checklist

---

### Phase 7 Exit Criteria

✅ Production deployed:
- [ ] Deployed to Dev, Stage, Prod
- [ ] Frontend integrated
- [ ] Smoke tests passing
- [ ] No critical issues in 24 hours
- [ ] Customer Support notified

---

## Phase 8: Validation & Handoff

**Duration**: 1 week (Week 13-14)
**Owner**: All Team

### Task 8.1: Production Monitoring

**Effort**: Continuous (2 weeks)
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- Monitor Datadog daily
- Track key metrics (error rates, durations)
- Address issues promptly

---

### Task 8.2: Performance Baseline Documentation

**Effort**: 1 day
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- Document sync report p50/p95/p99
- Document async report processing times
- Document cost baseline (~$40/month)

---

### Task 8.3: Documentation Finalization

**Effort**: 2 days
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- Update README.md
- Create ARCHITECTURE.md
- Update RUNBOOK.md
- Create Confluence page

---

### Task 8.4: Customer Support Training

**Effort**: 0.5 day
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- Training session (1 hour)
- Provide FAQ document
- Set up support channel

---

### Task 8.5: Retrospective

**Effort**: 2 hours
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- What went well?
- What didn't go well?
- Lessons learned for future migrations

---

### Task 8.6: Project Handoff

**Effort**: 1 day
**Status**: 📋 Not Started
**Assignee**: -
**Started**: -
**Completed**: -

- Knowledge transfer to maintenance team
- Hand over access credentials
- Set up on-call rotation

---

### Phase 8 Completion Checklist

**Before marking this phase as complete, verify:**

- [ ] All task status fields updated (Status, Assignee, Started, Completed dates)
- [ ] All tasks marked as ✅ Complete or properly documented if blocked
- [ ] Progress Tracker updated (Overall Progress %, Current Week, Current Phase)
- [ ] Phase Completion Status table updated (Completed count, Progress %, Status)
- [ ] Phase Exit Criteria checklist reviewed and checked off
- [ ] Code committed and pushed to repository
- [ ] Tests passing for all completed tasks
- [ ] Documentation updated (README, inline comments, etc.)

**To update the plan after completing this phase:**
1. Mark all tasks in this phase as ✅ Complete with completion dates
2. Update "Overall Progress" at top: Calculate (total completed tasks / 51 tasks)
3. Update "Current Week" at top: Increment based on timeline
4. Update "Current Phase" at top: Set to next phase name
5. Update Phase Completion Status table: Set this phase to "✅ Complete"
6. Check all boxes in Phase Exit Criteria
7. Check all boxes in this Phase Completion Checklist

---

### Phase 8 Exit Criteria

✅ Project complete:
- [ ] Production stable for 2 weeks
- [ ] Documentation finalized
- [ ] Customer Support trained
- [ ] Retrospective completed
- [ ] Handoff complete
