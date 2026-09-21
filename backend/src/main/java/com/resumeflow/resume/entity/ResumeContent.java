package com.resumeflow.resume.entity;

import tools.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable structured-content snapshot. Editable fields live in {@code content} JSONB. */
@Entity
@Table(
    name = "resume_contents",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_resume_contents_resume_version", columnNames = {"resume_id", "version_number"}))
public class ResumeContent {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "resume_id", nullable = false, updatable = false)
  private Resume resume;

  @Column(name = "version_number", nullable = false, updatable = false)
  private int versionNumber;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "content", nullable = false, columnDefinition = "jsonb", updatable = false)
  private JsonNode content;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ResumeContent() {
  }

  public ResumeContent(Resume resume, int versionNumber, JsonNode content) {
    this.id = UUID.randomUUID();
    this.resume = resume;
    this.versionNumber = versionNumber;
    this.content = content;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public Resume getResume() {
    return resume;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public JsonNode getContent() {
    return content;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
