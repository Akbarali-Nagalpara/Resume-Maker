package com.resumeflow.exception;

/** Base type for all ResumeFlow domain failures. Never logs document content. */
public abstract class ResumeFlowException extends RuntimeException {

  protected ResumeFlowException(String message) {
    super(message);
  }

  protected ResumeFlowException(String message, Throwable cause) {
    super(message, cause);
  }
}
