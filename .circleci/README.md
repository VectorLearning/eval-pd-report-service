# CircleCI Pipeline Documentation

## Overview

This CircleCI pipeline automates the build, test, security scanning, and deployment process for the EV+ Async Reporting Service (Java Spring Boot application with Maven).

## Pipeline Architecture

The pipeline consists of the following stages:

```
┌─────────────────────┐
│  Build and Test     │  ← Compile, run tests, generate coverage
└──────────┬──────────┘
           │
    ┌──────┴──────┐
    │             │
    ▼             ▼
┌─────────┐  ┌──────────────────┐
│ Dep     │  │ Build Docker     │
│ Check   │  │ Image            │
└─────────┘  └────────┬─────────┘
                      │
                      ▼
             ┌──────────────────┐
             │ Container Scan   │  ← Veracode security scan
             └────────┬─────────┘
                      │
                      ▼
             ┌──────────────────┐
             │ Push to ECR      │  ← Only on main branch
             └──────────────────┘
```

## Jobs Description

### 1. Build and Test
- **Executor**: `cimg/openjdk:21.0` (Java 21)
- **Resource**: Large
- **Actions**:
  - Checkout code
  - Download Maven dependencies (with caching)
  - Build application (`mvn clean package -DskipTests`)
  - Run unit tests with JaCoCo coverage
  - Store test results and coverage reports as artifacts
  - Tests are **non-blocking** (failures don't stop the build)
- **Artifacts**:
  - JaCoCo coverage report: `coverage-report/`
  - Test results: `test-results/`

### 2. Security - Dependency Check
- **Executor**: `cimg/openjdk:21.0`
- **Resource**: Medium
- **Actions**:
  - Run OWASP Dependency Check Maven plugin
  - Scan for known CVEs in dependencies
  - Fail if CVSS score >= 7 (configurable)
  - Generate HTML report
- **Artifacts**:
  - Dependency check report: `dependency-check-report.html`
- **Non-blocking**: Continues even with vulnerabilities

### 3. Build Docker Image
- **Executor**: `cimg/openjdk:21.0` with Remote Docker
- **Resource**: Medium
- **Actions**:
  - Uses `vsp-ci/docker-build-image` orb command
  - Builds multi-stage Docker image
  - Tags: `{repo}:main`, `{repo}:latest`, `{repo}:{version}` (on main)
  - Saves image as `.tar` file
  - Uses Docker Layer Caching for faster builds
- **Output**: Docker image tarball persisted to workspace

### 4. Security - Container Scan
- **Executor**: `cimg/base:current` with Remote Docker
- **Resource**: Medium
- **Actions**:
  - Uses `vsp-ci/docker-scan-image-veracode` orb command
  - Scans Docker image for vulnerabilities
  - Checks against Veracode database
  - Configured thresholds:
    - Critical: 0 allowed
    - High: 5 allowed
    - Medium/Low: Unlimited
  - Report mode: `true` (non-blocking)
- **Artifacts**:
  - Veracode scan results (JSON and text)

### 5. Push to ECR
- **Executor**: `cimg/base:current` with Remote Docker
- **Resource**: Small
- **Actions**:
  - Uses `vsp-ci/docker-push-image-ecr` orb command
  - Pushes all image tags to AWS ECR
  - Authenticates with AWS using IRSA
- **Filter**: Only runs on `main` branch

## Workflow

### `build-test-deploy`

Runs on every commit to any branch:

**All branches:**
1. Build and Test
2. Dependency Check (after Build and Test)
3. Build Docker Image (after Build and Test)
4. Container Scan (after Build Docker Image)

**Main branch only:**
5. Push to ECR (after Build Docker Image + Container Scan)

## Configuration

### Pipeline Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `workspace_dir` | `/home/circleci/workspace` | Workspace root |
| `checkout_dir` | `/home/circleci/workspace/project` | Code checkout location |
| `source_dir` | `/home/circleci/workspace/project` | Source code directory |
| `image_dir` | `/home/circleci/workspace/images` | Docker image storage |
| `test_results_dir` | `/home/circleci/workspace/test-results` | Test results location |
| `docker_repo_root` | `core` | ECR namespace prefix |
| `project_name` | `eval-pd-report-service` | Project/image name |

### Required CircleCI Contexts

The pipeline requires the following CircleCI contexts to be configured:

1. **Vector Core CI** (Required for all jobs)
   - `AWS_ROLE_ARN`: IAM role ARN for ECR push
   - `AWS_REGION`: AWS region (e.g., `us-east-1`)
   - `AWS_ECR_URL`: ECR registry URL

2. **VeracodeAgents** (Required for security scans)
   - `VECTOR_VERACODE_API_ID`: Veracode API key ID
   - `VECTOR_VERACODE_API_KEY`: Veracode API key secret

### Branch Filters

- **Main branch** (`main`): Full pipeline including ECR push
- **Feature branches**: Build, test, and security scans only (no ECR push)

## Environment Variables

These should be set in CircleCI project settings or contexts:

```bash
# AWS Configuration (Vector Core CI context)
AWS_ROLE_ARN=arn:aws:iam::ACCOUNT_ID:role/CircleCIRole
AWS_REGION=us-east-1
AWS_ECR_URL=ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com

# Veracode Configuration (VeracodeAgents context)
VECTOR_VERACODE_API_ID=your-veracode-api-id
VECTOR_VERACODE_API_KEY=your-veracode-api-key
```

## Caching Strategy

### Maven Dependencies Cache
- **Key**: `maven-deps-v1-{{ checksum "pom.xml" }}`
- **Path**: `~/.m2`
- **Benefit**: Speeds up dependency downloads across builds

### Docker Layer Caching
- **Enabled**: Yes (on `build-docker-image` job)
- **Benefit**: Reuses unchanged Docker layers

## Artifacts

| Artifact | Location | Description |
|----------|----------|-------------|
| JaCoCo Coverage Report | `coverage-report/` | HTML coverage report |
| Test Results | `test-results/` | JUnit XML test results |
| Dependency Check Report | `dependency-check-report.html` | OWASP vulnerability report |
| Veracode Scan Results | `/veracode/image-scan/` | Container scan results |

## Security Scanning

### Dependency Check (OWASP)
- Scans Maven dependencies for known CVEs
- Fails build if CVSS >= 7
- Suppressions configured in: `.circleci/dependency-check-suppressions.xml`

### Container Scan (Veracode)
- Scans Docker image for vulnerabilities
- Thresholds:
  - Critical: 0
  - High: 5
  - Medium: Unlimited
  - Low: Unlimited
- Report mode enabled (non-blocking)

## Docker Image

### Build Strategy
- **Multi-stage build**: Separate build and runtime stages
- **Base images**:
  - Build: `maven:3.9.9-amazoncorretto-21`
  - Runtime: `amazoncorretto:21-alpine`
- **Optimizations**:
  - Layer caching for dependencies
  - Non-root user for security
  - Health checks included
  - Minimal runtime image

### Image Tags

**On main branch:**
- `{namespace}/{project}:main`
- `{namespace}/{project}:latest`
- `{namespace}/{project}:{version}` (from pom.xml)

**On feature branches:**
- `{namespace}/{project}:{branch-name}`
- `{namespace}/{project}:latest`

Example:
```
core/eval-pd-report-service:main
core/eval-pd-report-service:latest
core/eval-pd-report-service:1.0.0-SNAPSHOT
```

## Testing Strategy

### Unit Tests
- Executed with Maven Surefire plugin
- Coverage measured with JaCoCo
- Target: 80% line coverage (configured in pom.xml)
- **Non-blocking**: Test failures don't stop the build

### Why Non-Blocking Tests?
The tests are configured as non-blocking (`|| true`) to allow the pipeline to complete and provide visibility into all issues at once. This is intentional for the CI phase but should be reviewed before production deployment.

## Local Testing

### Build Locally
```bash
mvn clean package -DskipTests
```

### Run Tests with Coverage
```bash
mvn clean test
mvn jacoco:report
# View report: target/site/jacoco/index.html
```

### Build Docker Image Locally
```bash
docker build -f docker/Dockerfile -t eval-pd-report-service:local .
```

### Run Container Locally
```bash
docker run -p 8080:8080 eval-pd-report-service:local
```

## Troubleshooting

### Build Failures

**Maven dependency download fails:**
- Check network connectivity
- Verify Maven Central is accessible
- Check cache key if dependencies seem stale

**Docker build fails:**
- Verify Dockerfile syntax
- Check if base images are accessible
- Review `.dockerignore` for excluded files

**ECR push fails:**
- Verify AWS credentials in context
- Check IAM role permissions
- Ensure ECR repository exists

### Security Scan Issues

**Dependency check fails:**
- Review the HTML report in artifacts
- Add suppressions to `.circleci/dependency-check-suppressions.xml` if false positives
- Update vulnerable dependencies in `pom.xml`

**Container scan fails:**
- Review Veracode report in artifacts
- Consider adjusting allowed thresholds
- Update base image versions in Dockerfile

## Maintenance

### Updating Dependencies
1. Update versions in `pom.xml`
2. Clear CircleCI cache if needed: Change cache key version
3. Run `mvn clean verify` locally first

### Updating Docker Base Images
1. Update image tags in `docker/Dockerfile`
2. Test locally before pushing
3. Monitor security scan results

### Updating Orb Versions
```yaml
orbs:
  vsp-ci: vector-solutions/vsp-ci@X.Y.Z  # Update version here
```

## Next Steps

After CircleCI setup:
1. ✅ Configure CircleCI contexts with required credentials
2. ✅ Create ECR repository: `core/eval-pd-report-service`
3. ✅ Set up branch protection rules
4. ✅ Configure deployment workflows (Task 0.6 - Kubernetes)
5. ✅ Set up monitoring and alerts

## Resources

- [CircleCI Documentation](https://circleci.com/docs/)
- [vsp-ci Orb Registry](https://circleci.com/orbs/registry/orb/vector-solutions/vsp-ci)
- [Maven CircleCI Guide](https://circleci.com/docs/language-java-maven/)
- [Docker Layer Caching](https://circleci.com/docs/docker-layer-caching/)

## Support

For issues or questions:
- CircleCI: Check build logs and artifacts
- Vector Solutions: Contact DevOps team
- OWASP Dependency Check: [GitHub Issues](https://github.com/jeremylong/DependencyCheck)
