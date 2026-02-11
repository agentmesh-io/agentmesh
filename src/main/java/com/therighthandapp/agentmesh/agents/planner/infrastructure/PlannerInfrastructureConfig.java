package com.therighthandapp.agentmesh.agents.planner.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Configuration: Planner Agent Infrastructure
 * 
 * Spring configuration for infrastructure beans used by the Planner Agent.
 */
@Configuration
public class PlannerInfrastructureConfig {
    
    /**
     * RestClient for calling Auto-BADS API
     */
    @Bean
    @ConditionalOnMissingBean(name = "plannerRestClient")
    public RestClient plannerRestClient(ObjectMapper objectMapper) {
        return RestClient.builder()
            .requestFactory(clientHttpRequestFactory())
            .messageConverters(converters -> {
                converters.clear();
                MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
                converter.setObjectMapper(objectMapper);
                converters.add(converter);
            })
            .build();
    }
    
    /**
     * HTTP Request Factory with timeouts
     */
    private ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30)); // SRS retrieval can be slow
        return factory;
    }
}
