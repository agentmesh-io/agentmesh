package com.therighthandapp.agentmesh.monitoring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for HikariCP connection pool.
 * Integrates with Spring Boot Actuator /actuator/health endpoint.
 */
@Component
public class ConnectionPoolHealthIndicator implements HealthIndicator {

    private final ConnectionPoolMonitor poolMonitor;

    @Autowired
    public ConnectionPoolHealthIndicator(ConnectionPoolMonitor poolMonitor) {
        this.poolMonitor = poolMonitor;
    }

    @Override
    public Health health() {
        ConnectionPoolMonitor.PoolStatistics stats = poolMonitor.getPoolStatistics();
        boolean isHealthy = poolMonitor.isPoolHealthy();

        Health.Builder builder = isHealthy ? Health.up() : Health.down();

        return builder
                .withDetail("maxPoolSize", stats.getMaxPoolSize())
                .withDetail("totalConnections", stats.getTotalConnections())
                .withDetail("activeConnections", stats.getActiveConnections())
                .withDetail("idleConnections", stats.getIdleConnections())
                .withDetail("threadsAwaitingConnection", stats.getThreadsAwaitingConnection())
                .withDetail("utilizationPercent", String.format("%.1f%%", stats.getUtilizationPercent()))
                .build();
    }
}
