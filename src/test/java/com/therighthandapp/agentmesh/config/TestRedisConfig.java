package com.therighthandapp.agentmesh.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.mockito.Mockito.mock;

/**
 * Test-specific Redis configuration that uses mocks to avoid needing a real Redis server.
 * Provides NoOpCacheManager to disable caching in tests.
 */
@TestConfiguration
@EnableCaching
@Profile("test")
public class TestRedisConfig {

    /**
     * Create an ObjectMapper that can handle Java 8 date/time types
     */
    @Bean
    @Primary
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.findAndRegisterModules();
        return mapper;
    }

    /**
     * Provide a mock RedisConnectionFactory to avoid needing real Redis
     */
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return mock(RedisConnectionFactory.class);
    }

    /**
     * Configure Redis template with JSR310-aware serializer and mock connection
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper redisObjectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer with JSR310 support for values
        GenericJackson2JsonRedisSerializer serializer = 
            new GenericJackson2JsonRedisSerializer(redisObjectMapper);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Use NoOpCacheManager to disable caching in tests completely
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        return new NoOpCacheManager();
    }
}
