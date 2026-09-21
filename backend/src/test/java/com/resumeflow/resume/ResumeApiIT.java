package com.resumeflow.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.resumeflow.resume.parser.SampleResumes;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Full API flow against real PostgreSQL and Redis containers using plain HTTP:
 * upload → editor → update → stale-write conflict → generate → preview →
 * download → versions → delete.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ResumeApiIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:18-alpine");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @LocalServerPort
  private int port;

  @Autowired
  private ObjectMapper objectMapper;

  private HttpClient http;
  private String base;

  @BeforeEach
  void setUp() {
    http = HttpClient.newHttpClient();
    base = "http://localhost:" + port + "/api/v1/resumes";
  }

  @Test
  void fullResumeLifecycle() throws Exception {
    Path tempDir = Files.createTempDirectory("resumeflow-it-");
    Path sample = SampleResumes.docxSample(tempDir);
    byte[] fileBytes = Files.readAllBytes(sample);

    JsonNode uploadBody = postMultipart(base, "file", "resume.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", fileBytes,
        201);
    String resumeId = uploadBody.get("resumeId").asText();
    assertNotNull(resumeId);

    JsonNode editor = get(base + "/" + resumeId, 200);
    assertEquals("Maya Chen", editor.get("content").get("personal").get("name").asText());
    assertEquals(2, editor.get("content").get("experience").size());
    long version = editor.get("resume").get("version").asLong();

    // Second editor read exercises the Redis cache path.
    get(base + "/" + resumeId, 200);

    JsonNode updatedContent = editor.get("content").deepCopy();
    ((tools.jackson.databind.node.ObjectNode) updatedContent.get("personal"))
        .put("title", "Staff Product Engineer");
    Map<String, Object> updatePayload = Map.of(
        "expectedVersion", version,
        "content", objectMapper.treeToValue(updatedContent, Object.class));
    JsonNode updated = putJson(base + "/" + resumeId + "/content",
        objectMapper.writeValueAsString(updatePayload), 200);
    assertEquals(2, updated.get("versionNumber").asInt());

    // Stale version must conflict.
    putJson(base + "/" + resumeId + "/content",
        objectMapper.writeValueAsString(updatePayload), 409);

    JsonNode generated = postEmpty(base + "/" + resumeId + "/generate", 200);
    assertNotNull(generated.get("storageKey").asText());

    get(base + "/" + resumeId + "/preview", 200);

    HttpResponse<byte[]> download = getBytes(base + "/" + resumeId + "/download", 200);
    assertTrue(download.body().length > 0);
    assertTrue(download.headers().firstValue("Content-Disposition").orElse("").contains(
        "attachment"));

    JsonNode versions = get(base + "/" + resumeId + "/versions", 200);
    assertTrue(versions.size() >= 3);

    get(base + "/" + resumeId + "/versions/1", 200);

    delete(base + "/" + resumeId, 204);
    get(base + "/" + resumeId, 404);
  }

  @Test
  void rejectsUnsupportedFiles() throws Exception {
    postMultipart(base, "file", "notes.txt", "text/plain",
        "hello".getBytes(StandardCharsets.UTF_8), 400);
  }

  @Test
  void rejectsUnknownResume() throws Exception {
    get(base + "/" + UUID.randomUUID(), 404);
  }

  private JsonNode get(String url, int expectedStatus) throws Exception {
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create(url)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(expectedStatus, response.statusCode(), "GET " + url + ": " + response.body());
    return objectMapper.readTree(response.body());
  }

  private HttpResponse<byte[]> getBytes(String url, int expectedStatus) throws Exception {
    HttpResponse<byte[]> response = http.send(
        HttpRequest.newBuilder(URI.create(url)).GET().build(),
        HttpResponse.BodyHandlers.ofByteArray());
    assertEquals(expectedStatus, response.statusCode(), "GET " + url);
    return response;
  }

  private JsonNode putJson(String url, String json, int expectedStatus) throws Exception {
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create(url))
            .PUT(HttpRequest.BodyPublishers.ofString(json))
            .header("Content-Type", "application/json")
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(expectedStatus, response.statusCode(), "PUT " + url + ": " + response.body());
    return response.body().isBlank()
        ? objectMapper.createObjectNode()
        : objectMapper.readTree(response.body());
  }

  private JsonNode postEmpty(String url, int expectedStatus) throws Exception {
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create(url))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(expectedStatus, response.statusCode(), "POST " + url + ": " + response.body());
    return objectMapper.readTree(response.body());
  }

  private void delete(String url, int expectedStatus) throws Exception {
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create(url)).DELETE().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(expectedStatus, response.statusCode(), "DELETE " + url + ": " + response.body());
  }

  private JsonNode postMultipart(
      String url, String field, String filename, String mimeType, byte[] fileBytes,
      int expectedStatus) throws Exception {
    String boundary = "----resumeflow" + System.nanoTime();
    List<byte[]> parts = new ArrayList<>();
    parts.add(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
    parts.add(("Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + filename
        + "\"\r\n").getBytes(StandardCharsets.UTF_8));
    parts.add(("Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    parts.add(fileBytes);
    parts.add(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    int length = parts.stream().mapToInt(part -> part.length).sum();
    byte[] body = new byte[length];
    int offset = 0;
    for (byte[] part : parts) {
      System.arraycopy(part, 0, body, offset, part.length);
      offset += part.length;
    }
    HttpResponse<String> response = http.send(
        HttpRequest.newBuilder(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(expectedStatus, response.statusCode(), "POST " + url + ": " + response.body());
    return objectMapper.readTree(response.body());
  }
}
