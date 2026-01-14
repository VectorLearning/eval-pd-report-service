package com.evplus.report.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * AWS Configuration using Spring Cloud AWS 3.4.2 Autoconfiguration.
 *
 * Spring Cloud AWS 3.4.2 automatically configures:
 * - AwsCredentialsProvider (DefaultCredentialsProvider in cloud, StaticCredentialsProvider in local)
 * - S3Client (via spring-cloud-aws-starter-s3)
 * - SqsAsyncClient (via spring-cloud-aws-starter-sqs)
 * - SesClient (via spring-cloud-aws-starter-ses)
 * - Region
 *
 * Configuration is driven by application.yml properties:
 * - spring.cloud.aws.credentials.* (access-key, secret-key for local)
 * - spring.cloud.aws.region.static
 * - spring.cloud.aws.s3.endpoint (for LocalStack)
 * - spring.cloud.aws.sqs.endpoint (for LocalStack)
 * - spring.cloud.aws.ses.endpoint (for LocalStack)
 * - spring.cloud.aws.s3.path-style-access-enabled (for LocalStack)
 *
 * DefaultCredentialsProvider chain (used in cloud):
 * 1. Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
 * 2. System properties (aws.accessKeyId, aws.secretAccessKey)
 * 3. Web Identity Token (IRSA) - AWS_ROLE_ARN, AWS_WEB_IDENTITY_TOKEN_FILE
 * 4. Container credentials (ECS)
 * 5. EC2 instance metadata
 *
 * This configuration ONLY defines S3Presigner, which is NOT autoconfigured by Spring Cloud AWS.
 * All other AWS clients are fully autoconfigured.
 */
@Configuration
public class AwsConfig {

    @Value("${spring.cloud.aws.region.static:us-east-2}")
    private String region;

    @Value("${spring.cloud.aws.s3.endpoint:}")
    private String s3Endpoint;

    @Value("${spring.cloud.aws.s3.path-style-access-enabled:false}")
    private boolean s3PathStyleEnabled;

    /**
     * S3Presigner bean for generating presigned URLs.
     *
     * Spring Cloud AWS does NOT autoconfigure S3Presigner (only S3Client is autoconfigured).
     * This bean uses the autoconfigured AwsCredentialsProvider from Spring Cloud AWS.
     *
     * Path-style access is critical for LocalStack presigned URL compatibility.
     *
     * @param credentialsProvider Injected from Spring Cloud AWS autoconfiguration
     * @return Configured S3Presigner
     */
    @Bean
    @ConditionalOnProperty(name = "aws.s3.presigner.enabled", havingValue = "true", matchIfMissing = true)
    public S3Presigner s3Presigner(AwsCredentialsProvider credentialsProvider) {
        S3Presigner.Builder builder = S3Presigner.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider);

        // Configure endpoint override for LocalStack
        if (s3Endpoint != null && !s3Endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(s3Endpoint))
                   .serviceConfiguration(
                       S3Configuration.builder()
                           .pathStyleAccessEnabled(s3PathStyleEnabled)
                           .build()
                   );
        }

        return builder.build();
    }
}
