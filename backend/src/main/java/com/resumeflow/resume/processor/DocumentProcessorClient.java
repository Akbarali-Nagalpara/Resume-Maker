package com.resumeflow.resume.processor;

import com.resumeflow.config.DocumentProcessorProperties;
import com.resumeflow.exception.ResumeParseException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
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
import tools.jackson.databind.JsonNode;

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
  public ProcessorResult processPdf(Path file, String filename, String checksum) {
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

  /**
   * Sends a PDF plus old/new content models to {@code POST /internal/patch/pdf}
   * for surgical in-place patching.
   */
  public PdfPatchResult patchPdf(
      Path file, String filename, JsonNode oldContent, JsonNode newContent) {
    MultipartBodyBuilder body = new MultipartBodyBuilder();
    body.part("file", new FileSystemResource(file))
        .filename(filename)
        .contentType(MediaType.APPLICATION_PDF);
    body.part("oldContent", oldContent.toString());
    body.part("content", newContent.toString());
    try {
      PatchResponse response =
          restClient
              .post()
              .uri("/internal/patch/pdf")
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(body.build())
              .retrieve()
              .body(PatchResponse.class);
      if (response == null || response.pdfBase64() == null) {
        throw new ResumeParseException("Document processor returned an empty patch", null);
      }
      return new PdfPatchResult(
          Base64.getDecoder().decode(response.pdfBase64()),
          response.warnings() == null
              ? java.util.List.of()
              : response.warnings().stream()
                  .map(w -> new PdfPatchResult.PatchWarning(w.nodeId(), w.reason(), w.detail()))
                  .toList(),
          response.patched() == null ? java.util.List.of() : response.patched());
    } catch (ResourceAccessException e) {
      throw new ResumeParseException(
          "Document processor at " + properties.baseUrl() + " is unreachable", e);
    } catch (RestClientResponseException e) {
      throw new ResumeParseException(
          "Document processor patch failed (status " + e.getStatusCode() + ")", e);
    }
  }

  private record PatchResponse(
      String pdfBase64,
      java.util.List<PatchWarningJson> warnings,
      java.util.List<String> patched) {
  }

  private record PatchWarningJson(String nodeId, String reason, String detail) {
  }
}
