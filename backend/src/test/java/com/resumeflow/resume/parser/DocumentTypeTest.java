package com.resumeflow.resume.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DocumentTypeTest {

  @Test
  void detectsDocxByZipMagicAndExtension() {
    byte[] head = {0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00};
    assertEquals(DocumentType.DOCX, DocumentType.detect("resume.docx", head));
  }

  @Test
  void detectsPdfByMagicAndExtension() {
    byte[] head = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00};
    assertEquals(DocumentType.PDF, DocumentType.detect("resume.pdf", head));
  }

  @Test
  void rejectsMismatchedMagicAndExtension() {
    byte[] zip = {0x50, 0x4B, 0x03, 0x04};
    assertThrows(
        com.resumeflow.exception.UnsupportedResumeFormatException.class,
        () -> DocumentType.detect("resume.pdf", zip));
  }

  @Test
  void rejectsUnknownFiles() {
    byte[] head = {0x00, 0x01, 0x02, 0x03};
    assertThrows(
        com.resumeflow.exception.UnsupportedResumeFormatException.class,
        () -> DocumentType.detect("resume.txt", head));
  }
}
