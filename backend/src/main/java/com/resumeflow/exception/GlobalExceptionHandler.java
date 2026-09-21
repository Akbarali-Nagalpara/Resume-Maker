package com.resumeflow.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Consistent RFC 7807 Problem Details for all API failures. JPA entities and
 * document content are never included in error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ResumeNotFoundException.class)
  public ProblemDetail handleNotFound(ResumeNotFoundException ex, HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, "Resume not found", ex.getMessage(), request);
  }

  @ExceptionHandler({UnsupportedResumeFormatException.class, InvalidResumeContentException.class})
  public ProblemDetail handleBadRequest(ResumeFlowException ex, HttpServletRequest request) {
    return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage(), request);
  }

  @ExceptionHandler(ResumeConflictException.class)
  public ProblemDetail handleConflict(ResumeConflictException ex, HttpServletRequest request) {
    return problem(HttpStatus.CONFLICT, "Version conflict", ex.getMessage(), request);
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ProblemDetail handleOptimisticLock(
      ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
    log.warn("Optimistic lock failure on {}", request.getRequestURI());
    return problem(
        HttpStatus.CONFLICT,
        "Version conflict",
        "The resume was modified concurrently. Reload and retry.",
        request);
  }

  @ExceptionHandler({ResumeParseException.class, StorageException.class})
  public ProblemDetail handleProcessing(ResumeFlowException ex, HttpServletRequest request) {
    log.error("Resume processing failure on {}", request.getRequestURI(), ex);
    return problem(
        HttpStatus.UNPROCESSABLE_ENTITY, "Processing failed", ex.getMessage(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining("; "));
    return problem(HttpStatus.BAD_REQUEST, "Validation failed", detail, request);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ProblemDetail handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST, "Invalid request",
        "Invalid value for '" + ex.getName() + "'.", request);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ProblemDetail handleMaxUploadSize(
      MaxUploadSizeExceededException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.PAYLOAD_TOO_LARGE, "File too large", "Uploaded file exceeds the size limit.",
        request);
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unexpected failure on {}", request.getRequestURI(), ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "An unexpected error occurred.",
        request);
  }

  private ProblemDetail problem(
      HttpStatus status, String title, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setInstance(URI.create(request.getRequestURI()));
    return problem;
  }
}
