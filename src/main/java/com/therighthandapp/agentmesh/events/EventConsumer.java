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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Kafka event consumer for AgentMesh.
 * Listens to events from Auto-BADS and other sources to trigger SDLC workflows.
 */
@Service
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);
    
    private final ProjectInitializationService projectInitializationService;
    private final ObjectMapper objectMapper;
    
    public EventConsumer(ProjectInitializationService projectInitializationService) {
        this.projectInitializationService = projectInitializationService;
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
            log.error("Failed to process SRS Generated Event. Error: {}", e.getMessage(), e);
            // TODO: Publish to dead letter topic
            throw new RuntimeException("Failed to process SRS event", e); // Rethrow to trigger Kafka retry
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
