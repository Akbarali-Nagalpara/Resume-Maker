package com.resumeflow.resume.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Top-level resume record. Mutable only for status; content lives in snapshots. */
@Entity
@Table(name = "resumes")
public class Resume {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "original_filename", nullable = false, length = 255)
  private String originalFilename;

  @Column(name = "mime_type", nullable = false, length = 100)
  private String mimeType;

  @Column(name = "checksum", nullable = false, length = 64)
  private String checksum;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ResumeStatus status;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Resume() {
  }

  public Resume(String originalFilename, String mimeType, String checksum) {
    this.id = UUID.randomUUID();
    this.originalFilename = originalFilename;
    this.mimeType = mimeType;
    this.checksum = checksum;
    this.status = ResumeStatus.PARSING;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public String getMimeType() {
    return mimeType;
  }

  public String getChecksum() {
    return checksum;
  }

  public ResumeStatus getStatus() {
    return status;
  }

  public void setStatus(ResumeStatus status) {
    this.status = status;
  }

  public Long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
