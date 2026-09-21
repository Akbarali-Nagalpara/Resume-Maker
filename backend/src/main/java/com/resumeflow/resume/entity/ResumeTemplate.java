package com.resumeflow.resume.entity;

import tools.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Normalized template/layout metadata extracted from the original document.
 * Written once during parsing and never modified afterwards: the original
 * design is the presentation source of truth for every generation.
 */
@Entity
@Table(name = "resume_templates")
public class ResumeTemplate {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "resume_id", nullable = false, unique = true, updatable = false)
  private Resume resume;

  @Column(name = "document_type", nullable = false, length = 20, updatable = false)
  private String documentType;

  @Column(name = "source_checksum", nullable = false, length = 64, updatable = false)
  private String sourceChecksum;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "template", nullable = false, columnDefinition = "jsonb", updatable = false)
  private JsonNode template;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ResumeTemplate() {
  }

  public ResumeTemplate(Resume resume, String documentType, String sourceChecksum, JsonNode template) {
    this.id = UUID.randomUUID();
    this.resume = resume;
    this.documentType = documentType;
    this.sourceChecksum = sourceChecksum;
    this.template = template;
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

  public String getDocumentType() {
    return documentType;
  }

  public String getSourceChecksum() {
    return sourceChecksum;
  }

  public JsonNode getTemplate() {
    return template;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
