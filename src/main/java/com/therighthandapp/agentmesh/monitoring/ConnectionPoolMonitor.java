package com.therighthandapp.agentmesh.monitoring;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Monitors HikariCP connection pool health and performance metrics.
 * Provides periodic logging of pool statistics for performance tuning.
 */
@Component
public class ConnectionPoolMonitor {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolMonitor.class);

    private final HikariDataSource hikariDataSource;
    private final HikariPoolMXBean poolMXBean;

    @Autowired
    public ConnectionPoolMonitor(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource) {
            this.hikariDataSource = (HikariDataSource) dataSource;
            this.poolMXBean = hikariDataSource.getHikariPoolMXBean();
        } else {
            throw new IllegalStateException("DataSource is not a HikariDataSource");
        }
    }

    /**
     * Logs connection pool statistics every 30 seconds.
     * Use these metrics to tune pool sizing and detect issues.
     */
    @Scheduled(fixedRate = 30000, initialDelay = 10000)
    public void logPoolStatistics() {
        if (poolMXBean != null) {
            int totalConnections = poolMXBean.getTotalConnections();
            int activeConnections = poolMXBean.getActiveConnections();
            int idleConnections = poolMXBean.getIdleConnections();
            int threadsAwaitingConnection = poolMXBean.getThreadsAwaitingConnection();

            logger.info("HikariCP Pool Stats - Total: {}, Active: {}, Idle: {}, Waiting: {}",
                    totalConnections, activeConnections, idleConnections, threadsAwaitingConnection);

            // Warn if pool is saturated
            if (activeConnections >= hikariDataSource.getMaximumPoolSize() * 0.9) {
                logger.warn("Connection pool is 90% saturated! Active: {}, Max: {}",
                        activeConnections, hikariDataSource.getMaximumPoolSize());
            }

            // Warn if threads are waiting for connections
            if (threadsAwaitingConnection > 0) {
                logger.warn("Threads waiting for connections: {}. Consider increasing pool size or optimizing queries.",
                        threadsAwaitingConnection);
            }

            // Warn if many idle connections (pool may be oversized)
            if (idleConnections > hikariDataSource.getMaximumPoolSize() * 0.7) {
                logger.info("High idle connection count: {}. Pool may be oversized.", idleConnections);
            }
        }
    }

    /**
     * Gets current pool statistics as a snapshot.
     * Useful for on-demand monitoring or REST endpoints.
     */
    public PoolStatistics getPoolStatistics() {
        if (poolMXBean == null) {
            return new PoolStatistics(0, 0, 0, 0, 0);
        }

        return new PoolStatistics(
                hikariDataSource.getMaximumPoolSize(),
                poolMXBean.getTotalConnections(),
                poolMXBean.getActiveConnections(),
                poolMXBean.getIdleConnections(),
                poolMXBean.getThreadsAwaitingConnection()
        );
    }

    /**
     * Checks if pool is healthy based on utilization and wait time.
     */
    public boolean isPoolHealthy() {
        if (poolMXBean == null) {
            return false;
        }

        int activeConnections = poolMXBean.getActiveConnections();
        int maxPoolSize = hikariDataSource.getMaximumPoolSize();
        int threadsWaiting = poolMXBean.getThreadsAwaitingConnection();

        // Pool is unhealthy if:
        // 1. More than 95% utilized
        // 2. Threads are waiting for connections
        boolean isUnhealthy = (activeConnections >= maxPoolSize * 0.95) || (threadsWaiting > 0);

        return !isUnhealthy;
    }

    /**
     * Immutable snapshot of connection pool statistics.
     */
    public static class PoolStatistics {
        private final int maxPoolSize;
        private final int totalConnections;
        private final int activeConnections;
        private final int idleConnections;
        private final int threadsAwaitingConnection;

        public PoolStatistics(int maxPoolSize, int totalConnections, int activeConnections,
                              int idleConnections, int threadsAwaitingConnection) {
            this.maxPoolSize = maxPoolSize;
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
            this.threadsAwaitingConnection = threadsAwaitingConnection;
        }

        public int getMaxPoolSize() { return maxPoolSize; }
        public int getTotalConnections() { return totalConnections; }
        public int getActiveConnections() { return activeConnections; }
        public int getIdleConnections() { return idleConnections; }
        public int getThreadsAwaitingConnection() { return threadsAwaitingConnection; }

        public double getUtilizationPercent() {
            return maxPoolSize > 0 ? (activeConnections * 100.0 / maxPoolSize) : 0.0;
        }

        @Override
        public String toString() {
            return String.format("PoolStatistics{max=%d, total=%d, active=%d, idle=%d, waiting=%d, util=%.1f%%}",
                    maxPoolSize, totalConnections, activeConnections, idleConnections,
                    threadsAwaitingConnection, getUtilizationPercent());
        }
    }
}
