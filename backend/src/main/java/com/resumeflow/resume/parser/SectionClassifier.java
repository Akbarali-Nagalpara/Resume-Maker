package com.resumeflow.resume.parser;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared heuristics for section and contact-line detection, used by every
 * format parser so DOCX and PDF extraction behave consistently.
 */
public final class SectionClassifier {

  private static final Map<String, String> KEYWORDS = Map.ofEntries(
      Map.entry("summary", "summary"),
      Map.entry("profile", "summary"),
      Map.entry("objective", "summary"),
      Map.entry("about", "summary"),
      Map.entry("skill", "skills"),
      Map.entry("technolog", "skills"),
      Map.entry("competenc", "skills"),
      Map.entry("experience", "experience"),
      Map.entry("employment", "experience"),
      Map.entry("work history", "experience"),
      Map.entry("work experience", "experience"),
      Map.entry("professional experience", "experience"),
      Map.entry("career history", "experience"),
      Map.entry("project", "projects"),
      Map.entry("education", "education"),
      Map.entry("qualification", "education"),
      Map.entry("academic", "education"),
      Map.entry("certification", "certifications"),
      Map.entry("certificate", "certifications"),
      Map.entry("license", "certifications"),
      Map.entry("licence", "certifications"),
      Map.entry("achievement", "achievements"),
      Map.entry("accomplishment", "achievements"),
      Map.entry("award", "achievements"),
      Map.entry("honor", "achievements"),
      Map.entry("language", "languages"),
      Map.entry("publication", "publications"),
      Map.entry("course", "courses"),
      Map.entry("training", "courses"),
      Map.entry("interest", "interests"),
      Map.entry("hobb", "interests"),
      Map.entry("reference", "references"),
      Map.entry("volunteer", "volunteering"));

  private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
  private static final Pattern PHONE =
      Pattern.compile("\\+?[\\d][\\d\\s().-]{6,}\\d");
  private static final Pattern URL =
      Pattern.compile("(https?://[\\w./-]+|[\\w-]+\\.[a-z]{2,}(?:/[\\w./-]*)?)",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern DATE_RANGE =
      Pattern.compile("(19|20)\\d{2}\\s*[—–\\-to]+\\s*((19|20)\\d{2}|present|current)",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern BULLET_PREFIX =
      Pattern.compile("^[•\\-\\*o▪‣]\\s+|^\\d+[.)]\\s+");

  private SectionClassifier() {
  }

  /** Maps a heading line to a known section key, if any. */
  public static Optional<String> classifyHeading(String heading) {
    if (heading == null) {
      return Optional.empty();
    }
    String normalized = heading.trim().toLowerCase();
    return KEYWORDS.entrySet().stream()
        .filter(entry -> normalized.contains(entry.getKey()))
        .map(Map.Entry::getValue)
        .findFirst();
  }

  /** Heuristic: short lines that look like titles rather than body text. */
  public static boolean looksLikeHeading(String line) {
    if (line == null) {
      return false;
    }
    String text = line.trim();
    if (text.isEmpty() || text.length() > 60 || text.endsWith(".") || text.endsWith(",")) {
      return false;
    }
    if (classifyHeading(text).isPresent()) {
      return true;
    }
    long letters = text.chars().filter(Character::isLetter).count();
    if (letters < 3) {
      return false;
    }
    long upper = text.chars().filter(c -> Character.isUpperCase(c)).count();
    return (double) upper / letters > 0.7;
  }

  public static boolean isBullet(String line) {
    return line != null && BULLET_PREFIX.matcher(line.trim()).find();
  }

  public static String stripBullet(String line) {
    Matcher matcher = BULLET_PREFIX.matcher(line.trim());
    return matcher.replaceFirst("").trim();
  }

  public static boolean containsDateRange(String line) {
    return line != null && DATE_RANGE.matcher(line).find();
  }

  /** Extracts e-mail / phone / website fragments from a contact line. */
  public static ContactParts parseContactLine(String line) {
    String rest = line == null ? "" : line;
    String email = findAndRemove(EMAIL, rest);
    rest = removeFirst(EMAIL, rest);
    String phone = findAndRemove(PHONE, rest);
    rest = removeFirst(PHONE, rest);
    String website = findAndRemove(URL, rest);
    rest = removeFirst(URL, rest);
    String location = String.join(" ", rest.split("[|·•,;]")).trim()
        .replaceAll("\\s{2,}", " ");
    return new ContactParts(email, phone, website, location.isBlank() ? null : location);
  }

  public record ContactParts(String email, String phone, String website, String location) {
  }

  private static String find(Pattern pattern, String line) {
    if (line == null) {
      return null;
    }
    Matcher matcher = pattern.matcher(line);
    return matcher.find() ? matcher.group().trim() : null;
  }

  private static String findAndRemove(Pattern pattern, String line) {
    return find(pattern, line);
  }

  private static String removeFirst(Pattern pattern, String line) {
    if (line == null) {
      return "";
    }
    Matcher matcher = pattern.matcher(line);
    return matcher.find()
        ? (line.substring(0, matcher.start()) + " " + line.substring(matcher.end()))
        : line;
  }

  /** Splits a skill block on common separators. */
  public static List<String> splitSkills(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(text.split("[\\n|•·▪,;]+"))
        .map(String::trim)
        .filter(part -> !part.isBlank())
        .toList();
  }
}
