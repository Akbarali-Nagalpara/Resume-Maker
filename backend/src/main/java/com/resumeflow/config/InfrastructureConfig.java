package com.resumeflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared infrastructure beans. The {@link ObjectMapper} is Spring Boot's
 * auto-configured Jackson 3 mapper (shared with the web layer so JSONB
 * round-trips match API payloads). The Redis template backs the cache-aside
 * layer: PostgreSQL remains the source of truth.
 */
@Configuration
public class InfrastructureConfig {

  @Bean
  public RedisTemplate<String, Object> redisTemplate(
      RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());
    GenericJacksonJsonRedisSerializer valueSerializer =
        new GenericJacksonJsonRedisSerializer(objectMapper);
    template.setValueSerializer(valueSerializer);
    template.setHashValueSerializer(valueSerializer);
    template.afterPropertiesSet();
    return template;
  }
}
