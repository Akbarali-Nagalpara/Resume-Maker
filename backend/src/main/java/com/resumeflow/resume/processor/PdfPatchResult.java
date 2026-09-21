package com.resumeflow.resume.processor;

import java.util.List;

/** Result of surgical PDF patching: patched bytes plus explicit warnings. */
public record PdfPatchResult(byte[] pdf, List<PatchWarning> warnings, List<String> patched) {

  public record PatchWarning(String nodeId, String reason, String detail) {
  }
}
