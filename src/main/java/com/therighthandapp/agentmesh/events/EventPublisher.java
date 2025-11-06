package com.therighthandapp.agentmesh.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.therighthandapp.agentmesh.config.KafkaTopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka event publisher for AgentMesh outbound events
 */
@Service
public class EventPublisher {
    
    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    public EventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * Publish ProjectInitializedEvent to Kafka
     */
    public CompletableFuture<SendResult<String, String>> publishProjectInitialized(ProjectInitializedEvent event) {
        String topic = KafkaTopicConfig.PROJECT_INITIALIZED_TOPIC;
        String key = event.correlationId();
        
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            log.info("Publishing ProjectInitializedEvent to topic '{}': projectId={}, correlationId={}", 
                     topic, event.projectId(), event.correlationId());
            
            return kafkaTemplate.send(topic, key, eventJson)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Successfully published ProjectInitializedEvent: projectId={}, partition={}, offset={}", 
                                     event.projectId(), 
                                     result.getRecordMetadata().partition(), 
                                     result.getRecordMetadata().offset());
                        } else {
                            log.error("Failed to publish ProjectInitializedEvent: projectId={}, error={}", 
                                      event.projectId(), ex.getMessage(), ex);
                        }
                    });
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ProjectInitializedEvent to JSON", e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Publish CodeGeneratedEvent to Kafka
     */
    public CompletableFuture<SendResult<String, String>> publishCodeGenerated(CodeGeneratedEvent event) {
        String topic = KafkaTopicConfig.CODE_GENERATED_TOPIC;
        String key = event.correlationId();
        
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            log.info("Publishing CodeGeneratedEvent to topic '{}': projectId={}", 
                     topic, event.projectId());
            
            return kafkaTemplate.send(topic, key, eventJson)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Successfully published CodeGeneratedEvent: projectId={}", 
                                     event.projectId());
                        } else {
                            log.error("Failed to publish CodeGeneratedEvent: projectId={}, error={}", 
                                      event.projectId(), ex.getMessage(), ex);
                        }
                    });
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize CodeGeneratedEvent to JSON", e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Publish DeploymentReadyEvent to Kafka
     */
    public CompletableFuture<SendResult<String, String>> publishDeploymentReady(DeploymentReadyEvent event) {
        String topic = KafkaTopicConfig.DEPLOYMENT_READY_TOPIC;
        String key = event.correlationId();
        
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            log.info("Publishing DeploymentReadyEvent to topic '{}': projectId={}", 
                     topic, event.projectId());
            
            return kafkaTemplate.send(topic, key, eventJson)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Successfully published DeploymentReadyEvent: projectId={}", 
                                     event.projectId());
                        } else {
                            log.error("Failed to publish DeploymentReadyEvent: projectId={}, error={}", 
                                      event.projectId(), ex.getMessage(), ex);
                        }
                    });
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize DeploymentReadyEvent to JSON", e);
            return CompletableFuture.failedFuture(e);
        }
    }
}
