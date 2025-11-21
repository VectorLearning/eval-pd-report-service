package com.evplus.report.config.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DatabaseHealthIndicator.
 */
@ExtendWith(MockitoExtension.class)
class DatabaseHealthIndicatorTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private Statement statement;

    private DatabaseHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new DatabaseHealthIndicator(dataSource);
    }

    @Test
    void health_WhenDatabaseIsUp_ReturnsHealthyStatus() throws SQLException {
        // Arrange
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute("SELECT 1")).thenReturn(true);

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("database", "MySQL");
        assertThat(health.getDetails()).containsEntry("status", "Connected");
        assertThat(health.getDetails()).containsKey("responseTime");

        verify(dataSource).getConnection();
        verify(statement).execute("SELECT 1");
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void health_WhenDatabaseConnectionFails_ReturnsDownStatus() throws SQLException {
        // Arrange
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("database", "MySQL");
        assertThat(health.getDetails()).containsEntry("status", "Disconnected");
        assertThat(health.getDetails()).containsEntry("error", "Connection failed");

        verify(dataSource).getConnection();
    }

    @Test
    void health_WhenQueryExecutionFails_ReturnsDownStatus() throws SQLException {
        // Arrange
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute("SELECT 1")).thenThrow(new SQLException("Query failed"));

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("database", "MySQL");
        assertThat(health.getDetails()).containsEntry("status", "Disconnected");
        assertThat(health.getDetails()).containsEntry("error", "Query failed");

        verify(dataSource).getConnection();
        verify(statement).execute("SELECT 1");
    }

    @Test
    void health_MeasuresResponseTime() throws SQLException {
        // Arrange
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute("SELECT 1")).thenReturn(true);

        // Act
        Health health = healthIndicator.health();

        // Assert
        String responseTime = (String) health.getDetails().get("responseTime");
        assertThat(responseTime).endsWith("ms");
        assertThat(responseTime).isNotNull();
    }
}
