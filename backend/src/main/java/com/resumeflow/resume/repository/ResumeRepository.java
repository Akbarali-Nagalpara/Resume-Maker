package com.resumeflow.resume.repository;

import com.resumeflow.resume.entity.Resume;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {

  /**
   * Atomic compare-and-touch for optimistic concurrency: bumps the entity
   * version (driving JPA optimistic locking) only when it still matches the
   * caller's expectation. Returns the number of touched rows (0 means a
   * concurrent modification happened).
   */
  @Modifying
  @Query("UPDATE Resume r SET r.updatedAt = :now, r.version = r.version + 1"
      + " WHERE r.id = :id AND r.version = :expected")
  int compareAndTouch(@Param("id") UUID id, @Param("expected") Long expected,
      @Param("now") Instant now);
}
