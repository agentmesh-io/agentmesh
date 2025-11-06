package com.therighthandapp.agentmesh.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration for AgentMesh event-driven architecture.
 * Defines topics for publishing events about project lifecycle.
 */
@Configuration
public class KafkaTopicConfig {

    public static final String PROJECT_INITIALIZED_TOPIC = "agentmesh.project.initialized";
    public static final String CODE_GENERATED_TOPIC = "agentmesh.code.generated";
    public static final String DEPLOYMENT_READY_TOPIC = "agentmesh.deployment.ready";
    
    // Dead Letter Topics for error handling
    public static final String PROJECT_INITIALIZED_DLT = "agentmesh.project.initialized.dlt";
    public static final String CODE_GENERATED_DLT = "agentmesh.code.generated.dlt";
    public static final String DEPLOYMENT_READY_DLT = "agentmesh.deployment.ready.dlt";

    @Bean
    public NewTopic projectInitializedTopic() {
        return TopicBuilder
            .name(PROJECT_INITIALIZED_TOPIC)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic codeGeneratedTopic() {
        return TopicBuilder
            .name(CODE_GENERATED_TOPIC)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic deploymentReadyTopic() {
        return TopicBuilder
            .name(DEPLOYMENT_READY_TOPIC)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic projectInitializedDltTopic() {
        return TopicBuilder
            .name(PROJECT_INITIALIZED_DLT)
            .partitions(1)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic codeGeneratedDltTopic() {
        return TopicBuilder
            .name(CODE_GENERATED_DLT)
            .partitions(1)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic deploymentReadyDltTopic() {
        return TopicBuilder
            .name(DEPLOYMENT_READY_DLT)
            .partitions(1)
            .replicas(1)
            .build();
    }
}
