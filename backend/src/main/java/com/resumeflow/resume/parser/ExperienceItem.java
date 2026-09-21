package com.resumeflow.resume.parser;

import java.util.List;

public record ExperienceItem(
    String id,
    String role,
    String company,
    String location,
    String dates,
    List<String> bullets) {
}
