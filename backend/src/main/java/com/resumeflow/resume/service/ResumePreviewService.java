package com.resumeflow.resume.service;

import com.resumeflow.resume.dto.PreviewResponse;
import com.resumeflow.resume.entity.ResumeFileKind;
import com.resumeflow.resume.entity.ResumeVersion;
import com.resumeflow.resume.repository.ResumeFileRepository;
import com.resumeflow.resume.repository.ResumeVersionRepository;
import com.resumeflow.resume.storage.FileStorageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves the current preview: the latest generated artifact when present,
 * generating on demand otherwise. Preview metadata (not binaries) is what
 * gets cached in Redis.
 */
@Service
public class ResumePreviewService {

  private final ResumeVersionRepository versionRepository;
  private final ResumeFileRepository fileRepository;
  private final ResumeVersionService versionService;
  private final ResumeGenerationService generationService;
  private final FileStorageService storageService;

  public ResumePreviewService(
      ResumeVersionRepository versionRepository,
      ResumeFileRepository fileRepository,
      ResumeVersionService versionService,
      ResumeGenerationService generationService,
      FileStorageService storageService) {
    this.versionRepository = versionRepository;
    this.fileRepository = fileRepository;
    this.versionService = versionService;
    this.generationService = generationService;
    this.storageService = storageService;
  }

  @Transactional
  public PreviewResponse preview(UUID resumeId) {
    Optional<ResumeVersion> latest =
        versionRepository.findFirstByResumeIdOrderByVersionNumberDesc(resumeId);
    if (latest.isPresent() && latest.get().getGeneratedStorageKey() != null) {
      ResumeVersion version = latest.get();
      return new PreviewResponse(resumeId, version.getVersionNumber(),
          version.getGeneratedStorageKey(), mimeFor(resumeId));
    }
    var generated = generationService.generate(resumeId);
    return new PreviewResponse(resumeId, generated.versionNumber(), generated.storageKey(),
        generated.mimeType());
  }

  @Transactional(readOnly = true)
  public PreviewResponse currentPreview(UUID resumeId) {
    ResumeVersion version = versionService.latest(resumeId);
    if (version.getGeneratedStorageKey() == null) {
      return null;
    }
    return new PreviewResponse(resumeId, version.getVersionNumber(),
        version.getGeneratedStorageKey(), mimeFor(resumeId));
  }

  /**
   * Renders actual PDF pages (latest generated PDF when present, otherwise
   * the original PDF) to PNG. The preview shows the real document, never a
   * generic reconstruction. Only available for PDF resumes.
   */
  @Transactional(readOnly = true)
  public int pdfPageCount(UUID resumeId) {
    return loadPdf(resumeId).pageCount();
  }

  @Transactional(readOnly = true)
  public byte[] pdfPageImage(UUID resumeId, int page) {
    PdfDocument pdf = loadPdf(resumeId);
    if (page < 1 || page > pdf.pageCount()) {
      throw new com.resumeflow.exception.InvalidResumeContentException(
          "Page " + page + " out of range (1.." + pdf.pageCount() + ").");
    }
    try (PDDocument document = Loader.loadPDF(pdf.bytes())) {
      BufferedImage image = new PDFRenderer(document).renderImageWithDPI(page - 1, 150);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "png", out);
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot render preview page for " + resumeId, e);
    }
  }

  private PdfDocument loadPdf(UUID resumeId) {
    PreviewResponse current = currentPreview(resumeId);
    String storageKey;
    if (current != null && current.mimeType().contains("pdf")) {
      storageKey = current.storageKey();
    } else {
      var original = fileRepository
          .findFirstByResumeIdAndKindOrderByCreatedAtDesc(resumeId, ResumeFileKind.ORIGINAL)
          .orElseThrow(() -> new IllegalStateException("No original file for " + resumeId));
      if (!original.getMimeType().contains("pdf")) {
        throw new com.resumeflow.exception.InvalidResumeContentException(
            "Page preview is only available for PDF resumes.");
      }
      storageKey = original.getStorageKey();
    }
    try {
      var resource = storageService.load(storageKey);
      byte[] bytes;
      try (InputStream in = resource.getInputStream()) {
        bytes = in.readAllBytes();
      }
      try (PDDocument document = Loader.loadPDF(bytes)) {
        return new PdfDocument(bytes, document.getNumberOfPages());
      }
    } catch (Exception e) {
      throw new IllegalStateException("Cannot load PDF for preview of " + resumeId, e);
    }
  }

  private record PdfDocument(byte[] bytes, int pageCount) {
  }

  private String mimeFor(UUID resumeId) {
    return fileRepository
        .findFirstByResumeIdAndKindOrderByCreatedAtDesc(resumeId, ResumeFileKind.GENERATED)
        .map(com.resumeflow.resume.entity.ResumeFile::getMimeType)
        .orElse("application/octet-stream");
  }
}
