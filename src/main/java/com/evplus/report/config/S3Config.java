package com.evplus.report.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * AWS S3 Configuration.
 *
 * Configures S3Client and S3Presigner beans with environment-specific settings:
 * - Local: Uses LocalStack with static credentials
 * - Cloud: Uses IRSA (IAM Roles for Service Accounts) with instance profile
 */
@Configuration
public class S3Config {

    @Value("${spring.cloud.aws.region.static:us-east-2}")
    private String region;

    @Value("${spring.cloud.aws.s3.endpoint:}")
    private String s3Endpoint;

    @Value("${spring.cloud.aws.credentials.access-key:}")
    private String accessKey;

    @Value("${spring.cloud.aws.credentials.secret-key:}")
    private String secretKey;

    /**
     * S3Client bean for S3 operations (upload, download).
     *
     * - Local environment: Points to LocalStack with static credentials
     * - Cloud environment: Uses instance profile (IRSA) for authentication
     */
    @Bean
    public S3Client s3Client() {
        software.amazon.awssdk.services.s3.S3ClientBuilder builder = S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider());

        // Override endpoint for LocalStack in local environment
        if (s3Endpoint != null && !s3Endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(s3Endpoint))
                   .forcePathStyle(true);  // Required for LocalStack
        }

        return builder.build();
    }

    /**
     * S3Presigner bean for generating presigned URLs.
     * Configured to use path-style URLs for LocalStack compatibility.
     */
    @Bean
    public S3Presigner s3Presigner() {
        software.amazon.awssdk.services.s3.presigner.S3Presigner.Builder builder = S3Presigner.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider());

        // Override endpoint for LocalStack in local environment
        if (s3Endpoint != null && !s3Endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(s3Endpoint))
                   .serviceConfiguration(
                       software.amazon.awssdk.services.s3.S3Configuration.builder()
                           .pathStyleAccessEnabled(true)  // Required for LocalStack presigned URLs
                           .build()
                   );
        }

        return builder.build();
    }

    /**
     * Credentials provider: Static for local, DefaultCredentialsProvider for cloud.
     * DefaultCredentialsProvider supports IRSA (Web Identity Token) and falls back to instance profile.
     */
    private AwsCredentialsProvider credentialsProvider() {
        // Local environment with static credentials
        if (accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            );
        }

        // Cloud environment - use DefaultCredentialsProvider which supports IRSA
        // This includes: Environment variables, System properties, Web Identity Token (IRSA),
        // Container credentials, Instance profile
        return DefaultCredentialsProvider.create();
    }
}
