package com.resumeflow.resume.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SectionClassifierTest {

  @Test
  void classifiesKnownHeadings() {
    assertEquals("skills", SectionClassifier.classifyHeading("Technical Skills").orElseThrow());
    assertEquals("experience", SectionClassifier.classifyHeading("WORK EXPERIENCE").orElseThrow());
    assertEquals("education", SectionClassifier.classifyHeading("Education").orElseThrow());
    assertEquals("summary", SectionClassifier.classifyHeading("Profile").orElseThrow());
    assertEquals("projects", SectionClassifier.classifyHeading("Projects").orElseThrow());
    assertEquals("experience", SectionClassifier.classifyHeading("WORK EXPERIENCE").orElseThrow());
    assertEquals(
        "experience", SectionClassifier.classifyHeading("Professional Experience").orElseThrow());
    assertEquals("certifications", SectionClassifier.classifyHeading("Certifications").orElseThrow());
    assertEquals("languages", SectionClassifier.classifyHeading("Languages").orElseThrow());
    assertEquals("interests", SectionClassifier.classifyHeading("Hobbies").orElseThrow());
    assertTrue(SectionClassifier.classifyHeading("References available on request").isPresent());
    assertTrue(SectionClassifier.classifyHeading("Favorite movies").isEmpty());
  }

  @Test
  void detectsAllCapsHeadingsButNotBody() {
    assertTrue(SectionClassifier.looksLikeHeading("EXPERIENCE"));
    assertFalse(SectionClassifier.looksLikeHeading("Senior Product Engineer"));
    assertFalse(SectionClassifier.looksLikeHeading("Led delivery of a platform."));
  }

  @Test
  void parsesContactLines() {
    SectionClassifier.ContactParts parts =
        SectionClassifier.parseContactLine("maya@example.com | +1 415 555 0142 | San Francisco, CA");
    assertEquals("maya@example.com", parts.email());
    assertEquals("+1 415 555 0142", parts.phone());
    assertEquals("San Francisco CA", parts.location());
  }

  @Test
  void splitsSkills() {
    List<String> skills = SectionClassifier.splitSkills("Java | Spring Boot, PostgreSQL\nReact");
    assertEquals(List.of("Java", "Spring Boot", "PostgreSQL", "React"), skills);
  }

  @Test
  void detectsBulletsAndDateRanges() {
    assertTrue(SectionClassifier.isBullet("• Led delivery"));
    assertTrue(SectionClassifier.isBullet("- Reduced cost"));
    assertFalse(SectionClassifier.isBullet("Led delivery"));
    assertTrue(SectionClassifier.containsDateRange("2022 — Present"));
    assertFalse(SectionClassifier.containsDateRange("San Francisco, CA"));
  }
}
