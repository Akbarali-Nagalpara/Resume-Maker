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
    assertEquals("San Francisco, CA", parts.location());
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

  @Test
  void parsesContactFieldsSeparately() {
    SectionClassifier.ContactParts parts = SectionClassifier.parseContactLine(
        "+91 72010 91900 • nagalparaakbarali03@gmail.com • linkedin.com/in/akbarali-nagalpara • github.com/Akbarali-Nagalpara Bengaluru India");
    assertEquals("nagalparaakbarali03@gmail.com", parts.email());
    assertEquals("+91 72010 91900", parts.phone());
    assertEquals("github.com/Akbarali-Nagalpara", parts.github());
    assertEquals("linkedin.com/in/akbarali-nagalpara", parts.linkedin());
    assertEquals("Bengaluru, India", parts.location());
  }

  @Test
  void bareTechWordsAreNotUrls() {
    assertFalse(SectionClassifier.looksLikeUrl("B.Tech Computer Science"));
    assertFalse(SectionClassifier.looksLikeUrl("React.js"));
    assertTrue(SectionClassifier.looksLikeUrl("mayachen.dev"));
    assertTrue(SectionClassifier.looksLikeUrl("https://example.com/x"));
  }

  @Test
  void detectsMonthYearRanges() {
    assertTrue(SectionClassifier.containsDateRange("Feb 2026 – Aug 2026"));
    assertTrue(SectionClassifier.containsDateRange("2024–2026"));
    assertFalse(SectionClassifier.containsDateRange("+91 72010 91900"));
  }

  @Test
  void detectsStackTokens() {
    assertTrue(SectionClassifier.isStackToken("MySQL"));
    assertTrue(SectionClassifier.isStackToken("REST API"));
    assertTrue(SectionClassifier.isStackToken("React.js"));
    assertFalse(SectionClassifier.isStackToken("Cool thing."));
    assertFalse(SectionClassifier.isStackToken("A very short description here"));
  }

  @Test
  void bulletsAreNeverSectionHeadings() {
    assertFalse(SectionClassifier.isSectionHeading("- AWS CERTIFIED SOLUTIONS ARCHITECT", false, true));
    assertFalse(
        SectionClassifier.isSectionHeading("- Mentored interns on EDUCATION FIRST principles", false, true));
    assertFalse(SectionClassifier.isSectionHeading("• Led delivery.", false, true));
  }

  @Test
  void keywordHeadingsRequireTitleShape() {
    assertTrue(SectionClassifier.isSectionHeading("WORK EXPERIENCE", false, false));
    assertTrue(SectionClassifier.isSectionHeading("Technical Skills", false, false));
    // Body sentences mentioning keywords are not headings.
    assertFalse(
        SectionClassifier.isSectionHeading(
            "Mentored interns on EDUCATION FIRST principles", false, true));
    assertFalse(
        SectionClassifier.isSectionHeading(
            "Led education initiatives across three teams", false, true));
  }
}
