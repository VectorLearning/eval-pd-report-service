package com.evplus.report.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * OpenAPI (Swagger) Configuration.
 *
 * Auto-generates OpenAPI 3.0 specification and provides Swagger UI
 * for interactive API documentation and testing.
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    /**
     * Configure OpenAPI specification with metadata and security scheme.
     * Used for dev, stage, and prod environments (not local).
     */
    @Bean
    @Profile("!local")
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(apiInfo())
            .servers(List.of(
                new Server()
                    .url(contextPath.isEmpty() ? "/" : contextPath)
                    .description("Current Environment")
            ))
            .components(new Components()
                .addSecuritySchemes("bearer-jwt", securityScheme())
            )
            .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }

    /**
     * API metadata information.
     */
    private Info apiInfo() {
        return new Info()
            .title("EV+ Async Reporting Service API")
            .version("1.0.0")
            .description("""
                Microservice for generating and delivering district-level reports asynchronously.

                ## Features
                - Synchronous report generation for small datasets
                - Asynchronous processing with SQS for large datasets
                - Email notifications with presigned S3 URLs
                - Excel (XLSX) report generation
                - Configurable async/sync thresholds

                ## Authentication
                This API uses OAuth2/OIDC JWT-based authentication.
                Include the JWT token in the Authorization header: `Bearer <token>`

                Note: Authentication is disabled in local development environment.
                """)
            .contact(new Contact()
                .name("EV+ Development Team")
                .email("evplus-dev@example.com")
            )
            .license(new License()
                .name("Proprietary")
                .url("https://evplus.com/license")
            );
    }

    /**
     * Security scheme configuration for JWT bearer tokens.
     */
    private SecurityScheme securityScheme() {
        return new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description("JWT Bearer Token Authentication. Obtain token from your OIDC provider.");
    }

    /**
     * Local profile OpenAPI - No security scheme for local development.
     */
    @Bean
    @Profile("local")
    public OpenAPI localOpenAPI() {
        return new OpenAPI()
            .info(apiInfo()
                .description(apiInfo().getDescription() +
                    "\n\n**LOCAL DEVELOPMENT MODE** - Authentication is disabled.")
            )
            .servers(List.of(
                new Server()
                    .url("http://localhost:8080" + contextPath)
                    .description("Local Development")
            ));
    }
}
