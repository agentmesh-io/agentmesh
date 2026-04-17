package com.therighthandapp.agentmesh.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.therighthandapp.agentmesh.integration.ProjectInitializationResult;
import com.therighthandapp.agentmesh.integration.ProjectInitializationService;
import com.therighthandapp.agentmesh.integration.dto.SrsHandoffDto;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Kafka event consumer for AgentMesh.
 * Listens to events from Auto-BADS and other sources to trigger SDLC workflows.
 * Failed messages are published to a dead letter queue for later reprocessing.
 */
@Service
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);
    private static final String DLQ_TOPIC = "autobads.srs.generated.dlq";

    private final ProjectInitializationService projectInitializationService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    public EventConsumer(ProjectInitializationService projectInitializationService,
                         KafkaTemplate<String, String> kafkaTemplate) {
        this.projectInitializationService = projectInitializationService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * Listen for SRS Generated events from Auto-BADS
     * This is the primary integration point that triggers the SDLC workflow
     */
    @KafkaListener(
        topics = "autobads.srs.generated",
        groupId = "agentmesh-consumer"
    )
    public void consumeSrsGenerated(ConsumerRecord<String, String> record) {
        log.info("Received SRS Generated Event from Kafka");
        String eventJson = record.value();
        
        try {
            log.info("Event JSON payload: {}", eventJson.substring(0, Math.min(200, eventJson.length())));
            
            // Parse the event
            SrsGeneratedEventDto eventDto = objectMapper.readValue(eventJson, SrsGeneratedEventDto.class);
            log.info("Parsed SrsGeneratedEvent: projectName={}, correlationId={}", 
                     eventDto.projectName(), eventDto.correlationId());
            
            // Initialize project in AgentMesh
            ProjectInitializationResult result = projectInitializationService.initializeProject(
                    eventDto.srsData(), 
                    eventDto.correlationId()
            );
            
            if (result.success()) {
                log.info("Successfully initialized project: projectId={}, projectKey={}, correlationId={}", 
                         result.projectId(), result.projectKey(), result.correlationId());
            } else {
                log.error("Failed to initialize project: correlationId={}, error={}", 
                          result.correlationId(), result.errorMessage());
                // Don't throw - we've already logged the error
            }
            
        } catch (Exception e) {
            log.error("Failed to process SRS Generated Event. Publishing to DLQ. Error: {}", e.getMessage(), e);
            publishToDeadLetterQueue(eventJson, e);
            // Don't rethrow — message goes to DLQ instead of infinite Kafka retry
        }
    }

    /**
     * Dead Letter Queue listener — logs failed messages for monitoring/alerting.
     */
    @KafkaListener(
        topics = DLQ_TOPIC,
        groupId = "agentmesh-dlq-consumer"
    )
    public void consumeDeadLetterQueue(ConsumerRecord<String, String> record) {
        log.warn("DLQ message received: key={}, topic={}, partition={}, offset={}",
                record.key(), record.topic(), record.partition(), record.offset());
        log.warn("DLQ payload: {}", record.value().substring(0, Math.min(500, record.value().length())));
        // Future: store in DB for admin replay endpoint
    }

    /**
     * Publish failed message to dead letter queue with error metadata.
     */
    private void publishToDeadLetterQueue(String originalPayload, Exception error) {
        try {
            Map<String, Object> dlqMessage = Map.of(
                    "originalPayload", originalPayload,
                    "error", error.getMessage() != null ? error.getMessage() : "Unknown error",
                    "errorClass", error.getClass().getSimpleName(),
                    "failedAt", LocalDateTime.now().toString(),
                    "retryable", true
            );
            String dlqJson = objectMapper.writeValueAsString(dlqMessage);
            kafkaTemplate.send(DLQ_TOPIC, dlqJson);
            log.info("Published failed SRS event to DLQ: {}", DLQ_TOPIC);
        } catch (Exception dlqError) {
            log.error("CRITICAL: Failed to publish to DLQ. Original error: {}. DLQ error: {}",
                    error.getMessage(), dlqError.getMessage(), dlqError);
        }
    }
    
    /**
     * DTO for receiving SrsGeneratedEvent from Kafka
     * Must match the structure published by Auto-BADS
     */
    public record SrsGeneratedEventDto(
        Long ideaId,
        String projectName,
        SrsHandoffDto srsData,
        LocalDateTime generatedAt,
        String correlationId
    ) {}
}
