package com.therighthandapp.agentmesh.agents.planner.infrastructure;

import com.therighthandapp.agentmesh.agents.planner.ports.SrsRepository;
import com.therighthandapp.agentmesh.integration.dto.SrsHandoffDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Infrastructure Adapter: Auto-BADS SRS Repository
 * 
 * Implements the SrsRepository port by calling Auto-BADS REST API.
 * This is in the infrastructure layer - it knows about HTTP, REST, etc.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoBadsSrsRepository implements SrsRepository {
    
    private final RestClient restClient;
    
    @Value("${agentmesh.autobads.base-url:http://localhost:8083}")
    private String autoBadsBaseUrl;
    
    @Override
    public SrsHandoffDto retrieveSrs(String srsId) {
        log.debug("Retrieving SRS from Auto-BADS: {}", srsId);
        
        try {
            String url = String.format("%s/api/v1/srs/%s", autoBadsBaseUrl, srsId);
            log.debug("Calling Auto-BADS API: {}", url);
            
            ResponseEntity<SrsHandoffDto> response = restClient.get()
                .uri(url)
                .retrieve()
                .toEntity(SrsHandoffDto.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("Successfully retrieved SRS: {}", srsId);
                return response.getBody();
            } else {
                log.error("Empty response from Auto-BADS for SRS: {}", srsId);
                throw new SrsRetrievalException("Empty response from Auto-BADS", null);
            }
            
        } catch (HttpClientErrorException.NotFound e) {
            log.error("SRS not found in Auto-BADS: {}", srsId);
            throw new SrsNotFoundException(srsId);
        } catch (RestClientException e) {
            log.error("Failed to retrieve SRS from Auto-BADS: {}", srsId, e);
            throw new SrsRetrievalException("HTTP error calling Auto-BADS", e);
        }
    }
    
    @Override
    public boolean exists(String srsId) {
        try {
            String url = String.format("%s/api/v1/srs/%s", autoBadsBaseUrl, srsId);
            
            ResponseEntity<Void> response = restClient.head()
                .uri(url)
                .retrieve()
                .toBodilessEntity();
            
            return response.getStatusCode() == HttpStatus.OK;
            
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (RestClientException e) {
            log.warn("Error checking SRS existence: {}", srsId, e);
            return false;
        }
    }
}
