package com.resumeflow.exception;

import java.util.UUID;

public class ResumeNotFoundException extends ResumeFlowException {

  public ResumeNotFoundException(UUID resumeId) {
    super("Resume not found: " + resumeId);
  }
}
