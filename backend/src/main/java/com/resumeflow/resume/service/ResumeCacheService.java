package com.resumeflow.resume.service;

import tools.jackson.databind.JsonNode;
import com.resumeflow.resume.dto.EditorStateResponse;
import com.resumeflow.resume.dto.PreviewResponse;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis cache-aside operations. PostgreSQL remains the source of truth; all
 * entries carry TTLs and are evicted on writes. Binary artifacts are never
 * cached, only metadata/DTOs.
 */
public interface ResumeCacheService {

  Optional<EditorStateResponse> getEditor(UUID resumeId);

  void putEditor(UUID resumeId, EditorStateResponse state);

  Optional<JsonNode> getTemplate(UUID resumeId);

  void putTemplate(UUID resumeId, JsonNode template);

  Optional<PreviewResponse> getPreview(UUID resumeId);

  void putPreview(UUID resumeId, PreviewResponse preview);

  void evictResume(UUID resumeId);
}
