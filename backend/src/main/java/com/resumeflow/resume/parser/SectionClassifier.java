package com.resumeflow.resume.parser;

import java.util.ArrayList;
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
  private static final String TLDS =
      "com|org|net|io|dev|me|in|co|ai|app|tech|info|us|uk|edu|online|site";
  // Bare domains count only with a real TLD in standalone/delimited position
  // ("React.js", "B.Tech", "Node.js" must NOT match).
  private static final Pattern URL = Pattern.compile(
      "(https?://[\\w./-]+|[\\w-]+\\.(?:" + TLDS + ")(?:/[\\w./-]*)?(?=$|\\s*[|•/,;:]))",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern GITHUB =
      Pattern.compile("(?:https?://)?(?:www\\.)?github\\.com/[\\w.-]+/?",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern LINKEDIN = Pattern.compile(
      "(?:https?://)?(?:www\\.)?linkedin\\.com/in/[\\w.-]+/?", Pattern.CASE_INSENSITIVE);
  private static final String MONTHS =
      "(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*";
  private static final Pattern DATE_RANGE = Pattern.compile(
      "(?:\\b" + MONTHS + "\\s+)?(19|20)\\d{2}\\s*[—–\\-to]+\\s*(?:\\b" + MONTHS
          + "\\s+)?((19|20)\\d{2}|present|current)",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern BULLET_PREFIX =
      Pattern.compile("^(?:[•▪‣▶▸→⇒]\\s*|[-*o]\\s+|\\d+[.)]\\s+)");
  private static final java.util.Set<String> CONTINUATIONS = java.util.Set.of(
      ",", "-", "/", ":", "with", "and", "or", "the", "a", "an", "of", "to",
      "for", "in", "on", "by", "via", "as", "at", "from", "into", "using");

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

  /**
   * Authoritative heading test for block splitters. Bullet lines are never
   * headings. Unstyled keyword lines must be short and title-shaped; ALL-CAPS
   * titles additionally wait for the first real section so contact blocks
   * stay in the preamble.
   */
  public static boolean isSectionHeading(String line, boolean styled, boolean seenKnownSection) {
    String text = line == null ? "" : line.trim();
    if (text.isEmpty() || isBullet(text)) {
      return false;
    }
    if (styled) {
      return true;
    }
    if (text.length() > 60 || text.endsWith(".") || text.endsWith(",")) {
      return false;
    }
    // Date/GPA lines belong to items, never start sections.
    if (containsDateRange(text)) {
      return false;
    }
    boolean keyword = classifyHeading(text).isPresent();
    if (keyword && text.length() <= 32) {
      return true;
    }
    if (!seenKnownSection) {
      return false;
    }
    long letters = text.chars().filter(Character::isLetter).count();
    if (letters < 3) {
      return false;
    }
    long upper = text.chars().filter(Character::isUpperCase).count();
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

  /** Whether a value looks like a URL (website field validation). */
  public static boolean looksLikeUrl(String value) {
    return value != null && URL.matcher(value).find();
  }

  /** Extracts e-mail / phone / github / linkedin / website / location fragments. */
  public static ContactParts parseContactLine(String line) {
    String rest = line == null ? "" : line;
    String email = findAndRemove(EMAIL, rest);
    rest = removeFirst(EMAIL, rest);
    String phone = findAndRemove(PHONE, rest);
    rest = removeFirst(PHONE, rest);
    String github = findAndRemove(GITHUB, rest);
    rest = removeFirst(GITHUB, rest);
    String linkedin = findAndRemove(LINKEDIN, rest);
    rest = removeFirst(LINKEDIN, rest);
    String website = findAndRemove(URL, rest);
    rest = removeFirst(URL, rest);
    return new ContactParts(email, phone, website, github, linkedin,
        normalizeLocation(rest));
  }

  public record ContactParts(
      String email, String phone, String website, String github, String linkedin,
      String location) {
  }

  /**
   * Rebuilds a location from leftover fragments, dropping URL/e-mail/phone
   * parts. Never returns contact data as a location.
   */
  public static String normalizeLocation(String rest) {
    if (rest == null) {
      return null;
    }
    List<String> fragments = new ArrayList<>();
    for (String chunk : rest.split("[|·•,;]")) {
      String part = chunk.trim();
      if (part.isEmpty()) {
        continue;
      }
      if (URL.matcher(part).find() || EMAIL.matcher(part).find()
          || PHONE.matcher(part).find() || part.contains("@")) {
        continue;
      }
      fragments.add(part);
    }
    if (fragments.isEmpty()) {
      return null;
    }
    String location = String.join(", ", fragments).replaceAll("\\s{2,}", " ").strip();
    if (location.contains(",")) {
      return location;
    }
    String[] words = location.split("\\s+");
    if (words.length == 2 && words[0].length() > 0 && words[1].length() > 0
        && Character.isUpperCase(words[0].charAt(0)) && words[0].substring(1).equals(
            words[0].substring(1).toLowerCase())
        && Character.isUpperCase(words[1].charAt(0)) && words[1].substring(1).equals(
            words[1].substring(1).toLowerCase())) {
      return words[0] + ", " + words[1];
    }
    return location;
  }

  public static boolean isContactLine(String line) {
    if (line == null) {
      return false;
    }
    ContactParts parts = parseContactLine(line);
    return line.contains("@") || parts.phone() != null || parts.github() != null
        || parts.linkedin() != null || parts.website() != null;
  }

  /** Splits "Company • City, Country" style headers into company + location. */
  public static String[] splitCompanyLocation(String line) {
    for (String sep : new String[] {"•", "|", "·"}) {
      if (line.contains(sep)) {
        String[] parts = java.util.Arrays.stream(line.split(java.util.regex.Pattern.quote(sep)))
            .map(String::trim)
            .filter(part -> !part.isEmpty())
            .toArray(String[]::new);
        if (parts.length >= 2) {
          return new String[] {parts[0], String.join(", ", java.util.Arrays.copyOfRange(parts, 1, parts.length))};
        }
        if (parts.length == 1) {
          return new String[] {parts[0], null};
        }
      }
    }
    String trimmed = line.trim();
    return new String[] {trimmed.isEmpty() ? null : trimmed, null};
  }

  /** Whether a line looks like a stack chip (short tech token). */
  public static boolean isStackToken(String line) {
    String token = line == null ? "" : line.strip();
    if (token.isEmpty() || token.length() >= 40) {
      return false;
    }
    if (token.chars().allMatch(c -> Character.isUpperCase(c) || !Character.isLetter(c))
        && token.chars().anyMatch(Character::isLetter)) {
      return true;
    }
    if (token.chars().anyMatch(Character::isDigit)) {
      return true;
    }
    if (token.matches("^[\\w+-]+(\\.[\\w+-]+)+$")) {
      return true;
    }
    String[] words = token.split("\\s+");
    if (words.length == 1) {
      return true;
    }
    return words.length <= 3
        && java.util.Arrays.stream(words)
            .allMatch(word -> !word.isEmpty()
                && (Character.isUpperCase(word.charAt(0)) || word.chars().anyMatch(Character::isDigit)));
  }

  /** Whether a line likely continues on the next line (ends with a
   * preposition, conjunction or fragment punctuation). */
  public static boolean continues(String previous) {
    if (previous == null) {
      return false;
    }
    String text = previous.strip();
    if (text.endsWith(",") || text.endsWith("-") || text.endsWith("/")
        || text.endsWith(":")) {
      return true;
    }
    String[] words = text.split("\\s+");
    if (words.length == 0) {
      return false;
    }
    String last = words[words.length - 1].replaceAll("[•·,;:()\"']", "").toLowerCase();
    return CONTINUATIONS.contains(last);
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
