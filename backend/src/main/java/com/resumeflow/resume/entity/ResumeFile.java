package com.resumeflow.resume.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Immutable artifact metadata. Binary bytes live in file storage, never in the DB. */
@Entity
@Table(name = "resume_files")
public class ResumeFile {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "resume_id", nullable = false, updatable = false)
  private Resume resume;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 20, updatable = false)
  private ResumeFileKind kind;

  @Column(name = "storage_key", nullable = false, unique = true, length = 512, updatable = false)
  private String storageKey;

  @Column(name = "mime_type", nullable = false, length = 100, updatable = false)
  private String mimeType;

  @Column(name = "size_bytes", nullable = false, updatable = false)
  private long sizeBytes;

  @Column(name = "checksum", nullable = false, length = 64, updatable = false)
  private String checksum;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ResumeFile() {
  }

  public ResumeFile(
      Resume resume,
      ResumeFileKind kind,
      String storageKey,
      String mimeType,
      long sizeBytes,
      String checksum) {
    this.id = UUID.randomUUID();
    this.resume = resume;
    this.kind = kind;
    this.storageKey = storageKey;
    this.mimeType = mimeType;
    this.sizeBytes = sizeBytes;
    this.checksum = checksum;
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

  public ResumeFileKind getKind() {
    return kind;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public String getMimeType() {
    return mimeType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public String getChecksum() {
    return checksum;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
