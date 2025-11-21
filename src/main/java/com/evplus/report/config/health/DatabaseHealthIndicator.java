package com.evplus.report.config.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Custom Health Indicator for Database connectivity.
 *
 * Executes a simple query to verify database is accessible and responsive.
 */
@Component("database")
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // Execute simple query to verify database connectivity
            long startTime = System.currentTimeMillis();
            statement.execute("SELECT 1");
            long responseTime = System.currentTimeMillis() - startTime;

            return Health.up()
                .withDetail("database", "MySQL")
                .withDetail("responseTime", responseTime + "ms")
                .withDetail("status", "Connected")
                .build();

        } catch (SQLException e) {
            return Health.down()
                .withDetail("database", "MySQL")
                .withDetail("error", e.getMessage())
                .withDetail("status", "Disconnected")
                .build();
        }
    }
}
