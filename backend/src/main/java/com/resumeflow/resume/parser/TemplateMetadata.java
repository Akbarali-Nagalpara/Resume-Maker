package com.resumeflow.resume.parser;

import tools.jackson.databind.JsonNode;
import java.util.List;

/**
 * Normalized template/layout metadata. This is the presentation source of truth
 * for generation: content changes must never alter these values.
 *
 * <ul>
 *   <li>{@code mappings} link content paths (e.g. {@code experience[0].bullets[1]})
 *       to source node identifiers (e.g. DOCX paragraph indexes).</li>
 *   <li>{@code properties} holds format-specific detail (OOXML styles, PDF geometry).</li>
 * </ul>
 */
public record TemplateMetadata(
    DocumentType documentType,
    String sourceChecksum,
    PageGeometry page,
    List<SectionAnchor> sections,
    JsonNode mappings,
    JsonNode properties) {

  public record PageGeometry(
      double widthPoints,
      double heightPoints,
      double marginTopPoints,
      double marginBottomPoints,
      double marginLeftPoints,
      double marginRightPoints) {
  }

  public record SectionAnchor(String key, String title, int position, String sourceNodeId) {
  }
}
