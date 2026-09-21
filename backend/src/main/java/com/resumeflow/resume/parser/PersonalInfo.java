package com.resumeflow.resume.parser;

/** Editable personal block. All fields are optional except {@code name}. */
public record PersonalInfo(
    String name,
    String title,
    String email,
    String phone,
    String location,
    String website,
    String github,
    String linkedin) {

  public PersonalInfo(String name, String title, String email, String phone, String location,
      String website) {
    this(name, title, email, phone, location, website, null, null);
  }
}
