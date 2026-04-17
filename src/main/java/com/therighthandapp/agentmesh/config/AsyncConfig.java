package com.therighthandapp.agentmesh.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async execution configuration for MAST detection.
 * 
 * Configures a dedicated thread pool for asynchronous MAST detection,
 * ensuring detection doesn't block the main application threads.
 * 
 * Pool Configuration:
 * - Core pool size: 10 threads (always active)
 * - Max pool size: 50 threads (scales up under load)
 * - Queue capacity: 100 tasks (buffer for bursts)
 * - Thread name prefix: "MAST-Async-" (easier debugging)
 * 
 * This configuration supports 100+ concurrent agents with minimal latency.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Thread pool executor for MAST detection tasks.
     * 
     * Sizing rationale:
     * - Core size (10): Handles baseline load without creating/destroying threads
     * - Max size (50): Allows scaling for 100-agent scenarios
     * - Queue (100): Absorbs bursts without rejecting tasks
     * - Keep alive (60s): Idle threads above core size are terminated after 60s
     * 
     * @return Configured executor for @Async("mastExecutor") tasks
     */
    @Bean(name = "mastExecutor")
    Executor mastExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core pool size - always active threads
        executor.setCorePoolSize(10);
        
        // Maximum pool size - scales up to handle load spikes
        executor.setMaxPoolSize(50);
        
        // Queue capacity - buffer for burst traffic
        executor.setQueueCapacity(100);
        
        // Thread name prefix for easier debugging
        executor.setThreadNamePrefix("MAST-Async-");
        
        // Keep alive time for idle threads above core size
        executor.setKeepAliveSeconds(60);
        
        // Caller-runs policy: if pool is saturated, caller thread executes
        // This provides graceful degradation instead of rejection
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        return executor;
    }

    /**
     * Thread pool executor for workflow execution tasks.
     * Separate from MAST to avoid contention.
     */
    @Bean(name = "workflowExecutor")
    Executor workflowExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("Workflow-Async-");
        executor.setKeepAliveSeconds(120);
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }
}
