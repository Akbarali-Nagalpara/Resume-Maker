package com.resumeflow.exception;

public class UnsupportedResumeFormatException extends ResumeFlowException {

  public UnsupportedResumeFormatException(String detail) {
    super("Unsupported resume format: " + detail);
  }
}
