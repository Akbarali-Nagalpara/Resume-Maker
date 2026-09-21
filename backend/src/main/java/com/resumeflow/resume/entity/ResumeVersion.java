package com.resumeflow.resume.entity;

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

/**
 * Immutable version record. A new row is appended for the initial parse, every
 * content update and every generation. Rows are never updated or deleted.
 */
@Entity
@Table(
    name = "resume_versions",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_resume_versions_resume_version", columnNames = {"resume_id", "version_number"}))
public class ResumeVersion {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "resume_id", nullable = false, updatable = false)
  private Resume resume;

  @Column(name = "version_number", nullable = false, updatable = false)
  private int versionNumber;

  @Column(name = "content_snapshot_id", updatable = false)
  private UUID contentSnapshotId;

  @Column(name = "template_snapshot_id", updatable = false)
  private UUID templateSnapshotId;

  @Column(name = "generated_storage_key", length = 512, updatable = false)
  private String generatedStorageKey;

  @Column(name = "generated_checksum", length = 64, updatable = false)
  private String generatedChecksum;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ResumeVersion() {
  }

  public ResumeVersion(
      Resume resume,
      int versionNumber,
      UUID contentSnapshotId,
      UUID templateSnapshotId,
      String generatedStorageKey,
      String generatedChecksum) {
    this.id = UUID.randomUUID();
    this.resume = resume;
    this.versionNumber = versionNumber;
    this.contentSnapshotId = contentSnapshotId;
    this.templateSnapshotId = templateSnapshotId;
    this.generatedStorageKey = generatedStorageKey;
    this.generatedChecksum = generatedChecksum;
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

  public UUID getContentSnapshotId() {
    return contentSnapshotId;
  }

  public UUID getTemplateSnapshotId() {
    return templateSnapshotId;
  }

  public String getGeneratedStorageKey() {
    return generatedStorageKey;
  }

  public String getGeneratedChecksum() {
    return generatedChecksum;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
