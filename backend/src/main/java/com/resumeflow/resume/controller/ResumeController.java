package com.resumeflow.resume.controller;

import com.resumeflow.resume.dto.ContentResponse;
import com.resumeflow.resume.dto.EditorStateResponse;
import com.resumeflow.resume.dto.GenerateResponse;
import com.resumeflow.resume.dto.PreviewResponse;
import com.resumeflow.resume.dto.UpdateContentRequest;
import com.resumeflow.resume.dto.UploadResponse;
import com.resumeflow.resume.dto.VersionResponse;
import com.resumeflow.resume.service.ResumeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * HTTP-only REST surface for the resume lifecycle. All business logic lives
 * in {@link ResumeService} and the domain services; JPA entities never leave
 * this layer (DTOs only).
 */
@RestController
@RequestMapping("/api/v1/resumes")
public class ResumeController {

  private final ResumeService resumeService;

  public ResumeController(ResumeService resumeService) {
    this.resumeService = resumeService;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public UploadResponse upload(@RequestParam("file") MultipartFile file) {
    return resumeService.upload(file);
  }

  @GetMapping("/{id}")
  public EditorStateResponse editorState(@PathVariable UUID id) {
    return resumeService.editorState(id);
  }

  @GetMapping("/{id}/preview")
  public PreviewResponse preview(@PathVariable UUID id) {
    return resumeService.preview(id);
  }

  @PutMapping("/{id}/content")
  public ContentResponse updateContent(
      @PathVariable UUID id, @Valid @RequestBody UpdateContentRequest request) {
    return resumeService.updateContent(id, request.expectedVersion(), request.content());
  }

  @PostMapping("/{id}/generate")
  public GenerateResponse generate(@PathVariable UUID id) {
    return resumeService.generate(id);
  }

  @GetMapping("/{id}/download")
  public ResponseEntity<Resource> download(
      @PathVariable UUID id,
      @RequestParam(name = "format", defaultValue = "source") String format) {
    ResumeService.DownloadedFile file = resumeService.download(id, format);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.mimeType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.filename()).build().toString())
        .body(file.resource());
  }

  @GetMapping("/{id}/versions")
  public List<VersionResponse> versions(@PathVariable UUID id) {
    return resumeService.versions(id);
  }

  @GetMapping("/{id}/versions/{version}")
  public VersionResponse version(@PathVariable UUID id, @PathVariable int version) {
    return resumeService.version(id, version);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    resumeService.delete(id);
  }
}
