package com.resumeflow.resume.service;

import tools.jackson.databind.JsonNode;
import com.resumeflow.resume.dto.EditorStateResponse;
import com.resumeflow.resume.dto.PreviewResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis cache-aside implementation. Failures degrade to direct database reads:
 * caching must never break the request path.
 */
@Service
public class RedisResumeCacheService implements ResumeCacheService {

  private static final Logger log = LoggerFactory.getLogger(RedisResumeCacheService.class);
  private static final Duration EDITOR_TTL = Duration.ofMinutes(10);
  private static final Duration TEMPLATE_TTL = Duration.ofMinutes(45);
  private static final Duration PREVIEW_TTL = Duration.ofMinutes(10);

  private final RedisTemplate<String, Object> redisTemplate;

  public RedisResumeCacheService(RedisTemplate<String, Object> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public Optional<EditorStateResponse> getEditor(UUID resumeId) {
    return get(key(resumeId, "editor"), EditorStateResponse.class);
  }

  @Override
  public void putEditor(UUID resumeId, EditorStateResponse state) {
    put(key(resumeId, "editor"), state, EDITOR_TTL);
  }

  @Override
  public Optional<JsonNode> getTemplate(UUID resumeId) {
    return get(key(resumeId, "template"), JsonNode.class);
  }

  @Override
  public void putTemplate(UUID resumeId, JsonNode template) {
    put(key(resumeId, "template"), template, TEMPLATE_TTL);
  }

  @Override
  public Optional<PreviewResponse> getPreview(UUID resumeId) {
    return get(key(resumeId, "preview"), PreviewResponse.class);
  }

  @Override
  public void putPreview(UUID resumeId, PreviewResponse preview) {
    put(key(resumeId, "preview"), preview, PREVIEW_TTL);
  }

  @Override
  public void evictResume(UUID resumeId) {
    try {
      redisTemplate.delete(
          java.util.List.of(
              key(resumeId, "editor"), key(resumeId, "template"), key(resumeId, "preview")));
    } catch (Exception e) {
      log.warn("Redis eviction failed for resume {}", resumeId, e);
    }
  }

  private static String key(UUID resumeId, String facet) {
    return "resume:" + resumeId + ":" + facet;
  }

  private <T> Optional<T> get(String key, Class<T> type) {
    try {
      Object value = redisTemplate.opsForValue().get(key);
      if (type.isInstance(value)) {
        return Optional.of(type.cast(value));
      }
      return Optional.empty();
    } catch (Exception e) {
      log.warn("Redis read failed for key {}", key, e);
      return Optional.empty();
    }
  }

  private void put(String key, Object value, Duration ttl) {
    try {
      redisTemplate.opsForValue().set(key, value, ttl);
    } catch (Exception e) {
      log.warn("Redis write failed for key {}", key, e);
    }
  }
}
