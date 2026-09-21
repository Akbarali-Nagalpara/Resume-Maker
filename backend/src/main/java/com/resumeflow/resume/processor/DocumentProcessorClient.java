package com.resumeflow.resume.processor;

import com.resumeflow.config.DocumentProcessorProperties;
import com.resumeflow.exception.ResumeParseException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * HTTP client for the Python document-processing service. Business services
 * must use this client instead of calling Python endpoints directly.
 *
 * <p>The service is stateless: file bytes travel with each call and no
 * business state lives in Python. Callers fall back to the embedded parsers
 * when the processor is disabled or unreachable.
 */
@Component
public class DocumentProcessorClient {

  private static final Logger log = LoggerFactory.getLogger(DocumentProcessorClient.class);

  private final DocumentProcessorProperties properties;
  private final RestClient restClient;

  public DocumentProcessorClient(DocumentProcessorProperties properties) {
    this.properties = properties;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(10));
    requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(10, properties.timeoutSeconds())));
    this.restClient =
        RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(requestFactory)
            .build();
  }

  public boolean isEnabled() {
    return properties.enabled();
  }

  public boolean isReachable() {
    if (!properties.enabled()) {
      return false;
    }
    try {
      restClient.get().uri("/health").retrieve().toBodilessEntity();
      return true;
    } catch (Exception e) {
      log.warn("Document processor at {} is unreachable: {}",
          properties.baseUrl(), e.getMessage());
      return false;
    }
  }

  /**
   * Sends a PDF to {@code POST /internal/process/pdf} and returns the
   * extracted content + layout model.
   */
  public ProcessorResult processPdf(java.nio.file.Path file, String filename, String checksum) {
    MultipartBodyBuilder body = new MultipartBodyBuilder();
    body.part("file", new FileSystemResource(file))
        .filename(filename)
        .contentType(MediaType.APPLICATION_PDF);
    try {
      ProcessorResult result =
          restClient
              .post()
              .uri("/internal/process/pdf")
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(body.build())
              .retrieve()
              .body(ProcessorResult.class);
      if (result == null) {
        throw new ResumeParseException("Document processor returned an empty response", null);
      }
      if (!checksum.equals(result.sourceChecksum())) {
        throw new ResumeParseException("Document processor checksum mismatch", null);
      }
      return result;
    } catch (ResourceAccessException e) {
      throw new ResumeParseException(
          "Document processor at " + properties.baseUrl() + " is unreachable", e);
    } catch (RestClientResponseException e) {
      throw new ResumeParseException(
          "Document processor rejected the file (status " + e.getStatusCode() + ")", e);
    }
  }
}
