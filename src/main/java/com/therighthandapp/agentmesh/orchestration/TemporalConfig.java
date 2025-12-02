package com.therighthandapp.agentmesh.orchestration;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Temporal workflow engine integration
 */
@Configuration
public class TemporalConfig {
    private static final Logger log = LoggerFactory.getLogger(TemporalConfig.class);

    @Value("${agentmesh.temporal.enabled:false}")
    private boolean temporalEnabled;

    @Value("${agentmesh.temporal.service-address:127.0.0.1:7233}")
    private String temporalServiceAddress;

    @Value("${agentmesh.temporal.namespace:default}")
    private String temporalNamespace;

    @Value("${agentmesh.temporal.task-queue:agentmesh-tasks}")
    private String taskQueue;

    @Bean
    WorkflowServiceStubs workflowServiceStubs() {
        if (!temporalEnabled) {
            log.info("Temporal is disabled. Orchestration will run in mock mode.");
            return null;
        }

        try {
            WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                    io.temporal.serviceclient.WorkflowServiceStubsOptions.newBuilder()
                            .setTarget(temporalServiceAddress)
                            .build()
            );
            log.info("Connected to Temporal service at {}", temporalServiceAddress);
            return service;
        } catch (Exception e) {
            log.warn("Failed to connect to Temporal service: {}. Running in mock mode.", e.getMessage());
            return null;
        }
    }

    @Bean
    WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
        if (serviceStubs == null) {
            return null;
        }

        return WorkflowClient.newInstance(serviceStubs,
                io.temporal.client.WorkflowClientOptions.newBuilder()
                        .setNamespace(temporalNamespace)
                        .build());
    }

    @Bean
    WorkerFactory workerFactory(WorkflowClient workflowClient, AgentActivityImpl activityImpl) {
        if (workflowClient == null) {
            log.info("Temporal worker not started (Temporal disabled)");
            return null;
        }

        WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
        Worker worker = factory.newWorker(taskQueue);

        // Register workflow implementations
        worker.registerWorkflowImplementationTypes(SdlcWorkflowImpl.class);

        // Register activity implementations
        worker.registerActivitiesImplementations(activityImpl);

        // Start workers
        factory.start();
        log.info("Temporal worker started for task queue: {}", taskQueue);

        return factory;
    }
}

