package com.resumeflow.resume.service;

import tools.jackson.databind.ObjectMapper;
import com.resumeflow.config.UploadProperties;
import com.resumeflow.exception.InvalidResumeContentException;
import com.resumeflow.exception.ResumeNotFoundException;
import com.resumeflow.exception.ResumeParseException;
import com.resumeflow.exception.UnsupportedResumeFormatException;
import com.resumeflow.resume.dto.UploadResponse;
import com.resumeflow.resume.entity.Resume;
import com.resumeflow.resume.entity.ResumeContent;
import com.resumeflow.resume.entity.ResumeFile;
import com.resumeflow.resume.entity.ResumeFileKind;
import com.resumeflow.resume.entity.ResumeStatus;
import com.resumeflow.resume.entity.ResumeTemplate;
import com.resumeflow.resume.parser.DocumentType;
import com.resumeflow.resume.parser.ParsedResume;
import com.resumeflow.resume.repository.ResumeContentRepository;
import com.resumeflow.resume.repository.ResumeFileRepository;
import com.resumeflow.resume.repository.ResumeRepository;
import com.resumeflow.resume.repository.ResumeTemplateRepository;
import com.resumeflow.resume.storage.FileStorageService;
import com.resumeflow.resume.storage.Sha256;
import com.resumeflow.resume.storage.StoredFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates uploads, stores the original artifact immutably and drives
 * parsing. The original file bytes are never modified after storage.
 */
@Service
public class ResumeUploadService {

  private static final Logger log = LoggerFactory.getLogger(ResumeUploadService.class);
  private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");
  private static final int MAGIC_HEAD_BYTES = 8;

  private final UploadProperties uploadProperties;
  private final FileStorageService storageService;
  private final ResumeParserService parserService;
  private final ResumeVersionService versionService;
  private final ResumeContentService contentService;
  private final ResumeRepository resumeRepository;
  private final ResumeFileRepository fileRepository;
  private final ResumeContentRepository contentRepository;
  private final ResumeTemplateRepository templateRepository;
  private final ObjectMapper objectMapper;

  public ResumeUploadService(
      UploadProperties uploadProperties,
      FileStorageService storageService,
      ResumeParserService parserService,
      ResumeVersionService versionService,
      ResumeContentService contentService,
      ResumeRepository resumeRepository,
      ResumeFileRepository fileRepository,
      ResumeContentRepository contentRepository,
      ResumeTemplateRepository templateRepository,
      ObjectMapper objectMapper) {
    this.uploadProperties = uploadProperties;
    this.storageService = storageService;
    this.parserService = parserService;
    this.versionService = versionService;
    this.contentService = contentService;
    this.resumeRepository = resumeRepository;
    this.fileRepository = fileRepository;
    this.contentRepository = contentRepository;
    this.templateRepository = templateRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public UploadResponse upload(MultipartFile file) {
    byte[] bytes = readAndValidate(file);
    String filename = safeFilename(file.getOriginalFilename());
    DocumentType type = DocumentType.detect(filename,
        Arrays.copyOf(bytes, Math.min(bytes.length, MAGIC_HEAD_BYTES)));
    String checksum = Sha256.hexOf(bytes);

    Resume resume = resumeRepository.save(
        new Resume(filename, mimeTypeFor(file.getContentType(), type), checksum));
    UUID resumeId = resume.getId();
    try {
      StoredFile stored = storageService.store(resumeId + "/original",
          type.name().toLowerCase(), new ByteArrayInputStream(bytes));
      fileRepository.save(new ResumeFile(resume, ResumeFileKind.ORIGINAL,
          stored.storageKey(), mimeTypeFor(file.getContentType(), type), stored.sizeBytes(),
          stored.sha256Hex()));

      Path tempFile = Files.createTempFile("resumeflow-upload-", "." + type.name().toLowerCase());
      try {
        Files.write(tempFile, bytes);
        ParsedResume parsed = parserService.parse(tempFile, type, checksum);
        log.info("Resume {} parsed (type={}, sections={})", resumeId, type,
            parsed.sections().stream().map(ParsedResume.SectionIndexEntry::key).toList());
        ResumeContent content = contentRepository.save(new ResumeContent(resume, 1,
            objectMapper.valueToTree(parsed.content())));
        ResumeTemplate template = templateRepository.save(new ResumeTemplate(resume,
            parsed.documentType().name(), checksum,
            objectMapper.valueToTree(parsed.template())));
        contentService.seedSections(resume, parsed);
        versionService.createInitial(resume, content.getId(), template.getId());
      } finally {
        Files.deleteIfExists(tempFile);
      }
      resume.setStatus(ResumeStatus.READY);
      return new UploadResponse(resumeId, ResumeStatus.READY.name(), 1);
    } catch (IOException e) {
      resume.setStatus(ResumeStatus.FAILED);
      throw new ResumeParseException("Cannot store uploaded resume", e);
    } catch (RuntimeException e) {
      resume.setStatus(ResumeStatus.FAILED);
      throw e;
    }
  }

  private byte[] readAndValidate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new InvalidResumeContentException("No file was uploaded.");
    }
    String extension = DocumentType.extensionOf(file.getOriginalFilename());
    if (!ALLOWED_EXTENSIONS.contains(extension)) {
      throw new UnsupportedResumeFormatException(
          "file extension '." + extension + "' is not supported (use PDF or DOCX)");
    }
    try {
      byte[] bytes = file.getBytes();
      if (bytes.length > uploadProperties.maxSizeBytes()) {
        throw new InvalidResumeContentException(
            "File exceeds the maximum size of " + uploadProperties.maxSizeMb() + " MB.");
      }
      return bytes;
    } catch (IOException e) {
      throw new ResumeParseException("Cannot read uploaded file", e);
    }
  }

  private static String safeFilename(String original) {
    if (original == null || original.isBlank()) {
      return "resume";
    }
    String base = Path.of(original).getFileName().toString();
    base = base.replaceAll("[^a-zA-Z0-9._-]", "_");
    return base.length() > 200 ? base.substring(base.length() - 200) : base;
  }

  private static String mimeTypeFor(String claimed, DocumentType type) {
    // Canonical MIME comes from the detected format (magic bytes), never from
    // the client claim alone.
    return type == DocumentType.PDF
        ? "application/pdf"
        : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  }

  @Transactional(readOnly = true)
  public Resume require(UUID resumeId) {
    return resumeRepository.findById(resumeId)
        .orElseThrow(() -> new ResumeNotFoundException(resumeId));
  }
}
