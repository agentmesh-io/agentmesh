package com.therighthandapp.agentmesh.orchestration;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

/**
 * Configuration for Temporal workflow engine integration.
 * All beans are conditional on agentmesh.temporal.enabled=true
 * Includes retry logic to handle Temporal server startup delays.
 */
@Configuration
@ConditionalOnProperty(name = "agentmesh.temporal.enabled", havingValue = "true")
public class TemporalConfig {
    private static final Logger log = LoggerFactory.getLogger(TemporalConfig.class);
    private static final int MAX_RETRIES = 10;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

    @Value("${agentmesh.temporal.service-address:127.0.0.1:7233}")
    private String temporalServiceAddress;

    @Value("${agentmesh.temporal.namespace:default}")
    private String temporalNamespace;

    @Value("${agentmesh.temporal.task-queue:agentmesh-tasks}")
    private String taskQueue;

    @PostConstruct
    public void init() {
        log.info("Temporal integration enabled. Service address: {}", temporalServiceAddress);
    }

    @Bean
    WorkflowServiceStubs workflowServiceStubs() {
        log.info("Connecting to Temporal service at {}", temporalServiceAddress);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                        WorkflowServiceStubsOptions.newBuilder()
                                .setTarget(temporalServiceAddress)
                                .setRpcTimeout(Duration.ofSeconds(30))
                                .build()
                );
                log.info("Successfully connected to Temporal service at {}", temporalServiceAddress);
                return service;
            } catch (Exception e) {
                log.warn("Attempt {}/{} - Failed to connect to Temporal service: {}",
                        attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting to retry Temporal connection", ie);
                    }
                }
            }
        }

        throw new RuntimeException("Failed to connect to Temporal service after " + MAX_RETRIES + " attempts");
    }

    @Bean
    WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
        log.info("Creating Temporal WorkflowClient for namespace: {}", temporalNamespace);
        return WorkflowClient.newInstance(serviceStubs,
                io.temporal.client.WorkflowClientOptions.newBuilder()
                        .setNamespace(temporalNamespace)
                        .build());
    }

    @Bean
    WorkerFactory workerFactory(WorkflowClient workflowClient, AgentActivityImpl activityImpl) {
        log.info("Starting Temporal worker for task queue: {}", taskQueue);

        WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
        Worker worker = factory.newWorker(taskQueue);

        // Register workflow implementations
        worker.registerWorkflowImplementationTypes(SdlcWorkflowImpl.class);

        // Register activity implementations
        worker.registerActivitiesImplementations(activityImpl);

        // Start workers
        factory.start();
        log.info("Temporal worker started successfully for task queue: {}", taskQueue);

        return factory;
    }
}

