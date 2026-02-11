package com.therighthandapp.agentmesh.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis configuration for caching blackboard entries and MAST violations.
 * Implements performance optimization to reduce database load.
 * Only active in non-test profiles.
 */
@Configuration
@EnableCaching
@Profile("!test")
public class RedisConfig {

    /**
     * Configure ObjectMapper with JSR310 support for Java 8 date/time types
     */
    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Configure Redis template for custom operations
     */
    @Bean
    RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper redisObjectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values with JSR310 support
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Configure cache manager with different TTLs for different cache types
     */
    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper redisObjectMapper) {
        // Default configuration - 1 hour TTL
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer(redisObjectMapper)
                )
            );

        // Blackboard entries - 30 minutes (frequently updated)
        RedisCacheConfiguration blackboardConfig = defaultConfig
            .entryTtl(Duration.ofMinutes(30));

        // Recent entries - 15 minutes (very frequently updated)
        RedisCacheConfiguration recentEntriesConfig = defaultConfig
            .entryTtl(Duration.ofMinutes(15));

        // Violations - 2 hours (less frequently accessed)
        RedisCacheConfiguration violationsConfig = defaultConfig
            .entryTtl(Duration.ofHours(2));

        // Tenant data - 6 hours (rarely changes)
        RedisCacheConfiguration tenantConfig = defaultConfig
            .entryTtl(Duration.ofHours(6));

        // Agent roles - 4 hours (rarely changes)
        RedisCacheConfiguration rolesConfig = defaultConfig
            .entryTtl(Duration.ofHours(4));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withCacheConfiguration("blackboardEntries", blackboardConfig)
            .withCacheConfiguration("recentEntries", recentEntriesConfig)
            .withCacheConfiguration("violations", violationsConfig)
            .withCacheConfiguration("tenants", tenantConfig)
            .withCacheConfiguration("agentRoles", rolesConfig)
            .transactionAware()
            .build();
    }
}
