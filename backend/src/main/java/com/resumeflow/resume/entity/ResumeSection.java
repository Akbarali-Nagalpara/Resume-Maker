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

/** Normalized section index for querying and section-level editing. */
@Entity
@Table(
    name = "resume_sections",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_resume_sections_resume_key", columnNames = {"resume_id", "section_key"}))
public class ResumeSection {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "resume_id", nullable = false, updatable = false)
  private Resume resume;

  @Column(name = "section_key", nullable = false, length = 100)
  private String sectionKey;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "position", nullable = false)
  private int position;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ResumeSection() {
  }

  public ResumeSection(Resume resume, String sectionKey, String title, int position) {
    this.id = UUID.randomUUID();
    this.resume = resume;
    this.sectionKey = sectionKey;
    this.title = title;
    this.position = position;
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

  public String getSectionKey() {
    return sectionKey;
  }

  public String getTitle() {
    return title;
  }

  public int getPosition() {
    return position;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
