package com.evplus.report.config.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisHealthIndicator.
 */
@ExtendWith(MockitoExtension.class)
class RedisHealthIndicatorTest {

    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    @Mock
    private RedisConnection redisConnection;

    private RedisHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new RedisHealthIndicator(redisConnectionFactory);
    }

    @Test
    void health_WhenRedisIsUp_ReturnsHealthyStatus() {
        // Arrange
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("redis", "Connected");
        assertThat(health.getDetails()).containsEntry("ping", "PONG");
        assertThat(health.getDetails()).containsKey("responseTime");

        verify(redisConnectionFactory).getConnection();
        verify(redisConnection).ping();
        verify(redisConnection).close();
    }

    @Test
    void health_WhenRedisConnectionFails_ReturnsDownStatus() {
        // Arrange
        when(redisConnectionFactory.getConnection())
            .thenThrow(new RuntimeException("Redis connection failed"));

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("redis", "Disconnected");
        assertThat(health.getDetails()).containsEntry("error", "Redis connection failed");

        verify(redisConnectionFactory).getConnection();
    }

    @Test
    void health_WhenPingFails_ReturnsDownStatus() {
        // Arrange
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenThrow(new RuntimeException("Ping failed"));

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("redis", "Disconnected");
        assertThat(health.getDetails()).containsEntry("error", "Ping failed");

        verify(redisConnectionFactory).getConnection();
        verify(redisConnection).ping();
    }

    @Test
    void health_MeasuresResponseTime() {
        // Arrange
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        // Act
        Health health = healthIndicator.health();

        // Assert
        String responseTime = (String) health.getDetails().get("responseTime");
        assertThat(responseTime).endsWith("ms");
        assertThat(responseTime).isNotNull();
    }

    @Test
    void health_ClosesConnectionAfterPing() {
        // Arrange
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        // Act
        healthIndicator.health();

        // Assert
        verify(redisConnection).close();
    }
}
