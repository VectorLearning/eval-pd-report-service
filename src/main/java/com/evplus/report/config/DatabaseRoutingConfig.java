package com.evplus.report.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Database Routing Configuration.
 *
 * Configures read/write database cluster routing:
 * - Write operations go to the primary/master database
 * - Read operations go to read replicas
 *
 * Uses AbstractRoutingDataSource with @Transactional(readOnly) flag
 * to determine routing.
 *
 * For local development, both read and write point to the same database.
 */
@Configuration
public class DatabaseRoutingConfig {

    /**
     * Local profile: Single datasource for both read and write operations.
     * Simplifies local development while maintaining production-like structure.
     */
    @Bean
    @Primary
    @Profile("local")
    public DataSource localDataSource(
        @Qualifier("writeDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();
    }

    /**
     * Non-local profiles: Configure read/write routing datasource.
     */
    @Bean
    @Primary
    @Profile({"dev", "stage", "prod"})
    public DataSource routingDataSource(
        @Qualifier("writeDataSource") DataSource writeDataSource,
        @Qualifier("readDataSource") DataSource readDataSource
    ) {
        RoutingDataSource routingDataSource = new RoutingDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DatabaseType.WRITE, writeDataSource);
        targetDataSources.put(DatabaseType.READ, readDataSource);

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(writeDataSource);

        // Use LazyConnectionDataSourceProxy to defer connection acquisition
        // until actual database operation is performed
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }

    /**
     * Write DataSource Properties.
     * Loaded from spring.datasource.* in application.yml
     */
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties writeDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * Read DataSource Properties.
     * Loaded from spring.datasource.read.* in application.yml
     *
     * Falls back to write datasource properties if read config not provided (local dev).
     */
    @Bean
    @ConfigurationProperties("spring.datasource.read")
    public DataSourceProperties readDataSourceProperties(
        @Qualifier("writeDataSourceProperties") DataSourceProperties fallback
    ) {
        DataSourceProperties properties = new DataSourceProperties();

        // If read properties not configured, use write properties (fallback for local)
        if (properties.getUrl() == null) {
            return fallback;
        }

        return properties;
    }

    /**
     * Write DataSource Bean.
     * Used for INSERT, UPDATE, DELETE operations.
     */
    @Bean
    @Profile({"dev", "stage", "prod"})
    public DataSource writeDataSource(
        @Qualifier("writeDataSourceProperties") DataSourceProperties properties
    ) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();

        // Write-specific pool settings (can be customized per environment)
        dataSource.setPoolName("WritePool");

        return dataSource;
    }

    /**
     * Read DataSource Bean.
     * Used for SELECT queries with @Transactional(readOnly=true).
     */
    @Bean
    @Profile({"dev", "stage", "prod"})
    public DataSource readDataSource(
        @Qualifier("readDataSourceProperties") DataSourceProperties properties
    ) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();

        // Read-specific pool settings (larger pool for read operations)
        dataSource.setPoolName("ReadPool");

        return dataSource;
    }

    /**
     * Database type enum for routing.
     */
    public enum DatabaseType {
        WRITE,
        READ
    }
}
