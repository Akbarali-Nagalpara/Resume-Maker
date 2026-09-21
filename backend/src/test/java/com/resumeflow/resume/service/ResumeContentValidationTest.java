package com.resumeflow.resume.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.resumeflow.exception.InvalidResumeContentException;
import com.resumeflow.resume.parser.ResumeContentModel;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ResumeContentValidationTest {

  private final ResumeContentService service =
      new ResumeContentService(null, null, null, null, null, new ObjectMapper());

  @Test
  void acceptsValidContentAndNormalizesLists() {
    ObjectNode content = new ObjectMapper().createObjectNode();
    ObjectNode personal = content.putObject("personal");
    personal.put("name", "Maya Chen");

    ResumeContentModel model = service.validate(content);

    assertEquals("Maya Chen", model.personal().name());
    assertTrue(model.skills().isEmpty());
    assertTrue(model.experience().isEmpty());
    assertTrue(model.additionalSections().isEmpty());
  }

  @Test
  void rejectsMissingName() {
    ObjectNode content = new ObjectMapper().createObjectNode();
    content.putObject("personal").put("name", "  ");

    assertThrows(InvalidResumeContentException.class, () -> service.validate(content));
  }

  @Test
  void rejectsNullContent() {
    assertThrows(InvalidResumeContentException.class,
        () -> service.validate(null));
  }

  @Test
  void rejectsWrongShape() {
    ObjectNode content = new ObjectMapper().createObjectNode();
    content.putArray("personal");

    assertThrows(InvalidResumeContentException.class, () -> service.validate(content));
  }
}
