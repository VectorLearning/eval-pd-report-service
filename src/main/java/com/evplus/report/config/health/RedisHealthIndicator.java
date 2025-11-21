package com.evplus.report.config.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Custom Health Indicator for Redis connectivity.
 *
 * Verifies Redis connection and measures response time.
 */
@Component("redis")
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public Health health() {
        try {
            long startTime = System.currentTimeMillis();

            RedisConnection connection = redisConnectionFactory.getConnection();
            String pong = connection.ping();
            connection.close();

            long responseTime = System.currentTimeMillis() - startTime;

            return Health.up()
                .withDetail("redis", "Connected")
                .withDetail("responseTime", responseTime + "ms")
                .withDetail("ping", pong)
                .build();

        } catch (Exception e) {
            return Health.down()
                .withDetail("redis", "Disconnected")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
