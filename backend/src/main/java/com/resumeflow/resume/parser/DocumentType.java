package com.resumeflow.resume.parser;

import com.resumeflow.exception.UnsupportedResumeFormatException;

/** Supported source document formats. Detection never trusts the client filename alone. */
public enum DocumentType {
  DOCX,
  PDF;

  private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
  private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};

  public static DocumentType detect(String filename, byte[] head) {
    String extension = extensionOf(filename);
    boolean zip = startsWith(head, ZIP_MAGIC);
    boolean pdf = startsWith(head, PDF_MAGIC);
    if (zip && (extension.equals("docx") || extension.equals("doc"))) {
      return DOCX;
    }
    if (pdf && extension.equals("pdf")) {
      return PDF;
    }
    throw new UnsupportedResumeFormatException(
        "expected a DOCX (OOXML) or PDF document, got file '" + filename + "'");
  }

  public static String extensionOf(String filename) {
    if (filename == null) {
      return "";
    }
    int dot = filename.lastIndexOf('.');
    return dot == -1 ? "" : filename.substring(dot + 1).toLowerCase();
  }

  private static boolean startsWith(byte[] head, byte[] magic) {
    if (head == null || head.length < magic.length) {
      return false;
    }
    for (int i = 0; i < magic.length; i++) {
      if (head[i] != magic[i]) {
        return false;
      }
    }
    return true;
  }
}
