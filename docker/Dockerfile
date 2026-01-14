# Multi-stage Dockerfile for EV+ Async Reporting Service
# Build stage uses Maven to compile and package the application
# Runtime stage uses slim JRE image for smaller footprint

# Build stage
FROM maven:3.9.9-amazoncorretto-21 AS builder

WORKDIR /build

# Copy pom.xml and download dependencies (better layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application (skip tests in Docker build - run in CI)
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM amazoncorretto:21-alpine

# Install dumb-init for proper signal handling
RUN apk add --no-cache dumb-init

# Install fontconfig and fonts for Apache POI Excel auto-sizing
# Required by POI's SheetUtil.getDefaultCharWidthAsFloat() for column width calculations
RUN apk add --no-cache fontconfig ttf-dejavu

# Create non-root user for security
RUN addgroup -g 1001 appuser && \
    adduser -D -u 1001 -G appuser appuser

WORKDIR /app

# Copy the JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose application port
EXPOSE 8080

# Health check endpoint
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ev-pd-report/v1/actuator/health || exit 1

# Use dumb-init to handle signals properly
ENTRYPOINT ["dumb-init", "--"]

# Run the application with recommended JVM options
CMD ["java", \
     "-XX:+UseContainerSupport", \
     "-XX:MaxRAMPercentage=75.0", \
     "-XX:+UseG1GC", \
     "-XX:+ExitOnOutOfMemoryError", \
     "-Djava.security.egd=file:/dev/./urandom", \
     "-jar", \
     "app.jar"]
