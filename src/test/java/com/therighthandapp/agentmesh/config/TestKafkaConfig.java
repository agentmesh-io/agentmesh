package com.therighthandapp.agentmesh.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Test configuration for Kafka beans.
 * Provides mock/stub Kafka beans for testing.
 */
@TestConfiguration
@Profile("test")
public class TestKafkaConfig {

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public KafkaTemplate<String, String> kafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringSerializer.class);
        
        ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(producerFactory);
    }
}
