package com.resumeflow.resume.parser;

import java.util.List;

/** Editable personal block. All fields are optional except {@code name}. */
public record PersonalInfo(
    String name,
    String title,
    String email,
    String phone,
    String location,
    String website) {
}
