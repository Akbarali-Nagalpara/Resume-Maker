package com.resumeflow.resume.parser;

import java.util.List;

/**
 * Normalized editable resume content. Unknown sections are preserved verbatim
 * in {@code additionalSections} so no user content is lost during round-trips.
 */
public record ResumeContentModel(
    PersonalInfo personal,
    String summary,
    List<String> skills,
    List<ExperienceItem> experience,
    List<ProjectItem> projects,
    List<EducationItem> education,
    List<AdditionalSection> additionalSections) {

  public record AdditionalSection(String key, String title, List<String> paragraphs) {
  }
}
