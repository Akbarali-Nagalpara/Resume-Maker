package com.resumeflow.resume.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.resumeflow.config.DocumentProcessorProperties;
import com.resumeflow.exception.ResumeParseException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** DocumentProcessorClient against a stub HTTP server (no real service needed). */
class DocumentProcessorClientTest {

  private static final String RESPONSE = """
      {"documentType":"PDF","content":{"personal":{"name":"Maya Chen"},"summary":null,\
      "skills":[],"experience":[],"projects":[],"education":[],"additionalSections":[]},\
      "template":{"documentType":"PDF","sourceChecksum":"%s","page":\
      {"widthPoints":595.0,"heightPoints":842.0,"marginTopPoints":72.0,\
      "marginBottomPoints":72.0,"marginLeftPoints":72.0,"marginRightPoints":72.0},\
      "sections":[],"mappings":{},"properties":{}},"sections":[],\
      "sourceChecksum":"%s"}\
      """;

  @Test
  void mapsProcessorResponse(@TempDir Path directory) throws Exception {
    Path file = directory.resolve("r.pdf");
    Files.write(file, new byte[] {0x25, 0x50, 0x44, 0x46});
    String checksum = com.resumeflow.resume.storage.Sha256.hexOf(
        Files.readAllBytes(file));
    String body = RESPONSE.formatted(checksum, checksum);
    HttpServer server = stubServer("/internal/process/pdf", 200, body);
    try {
      int port = server.getAddress().getPort();
      DocumentProcessorClient client = new DocumentProcessorClient(
          new DocumentProcessorProperties("http://127.0.0.1:" + port, true, 10));

      assertTrue(client.isReachable());
      ProcessorResult result = client.processPdf(file, "r.pdf", checksum);

      assertEquals("PDF", result.documentType());
      assertEquals("Maya Chen", result.content().get("personal").get("name").asText());
      assertEquals(checksum, result.sourceChecksum());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void wrapsUnreachableService(@TempDir Path directory) throws Exception {
    Path file = directory.resolve("r.pdf");
    Files.write(file, new byte[] {0x25, 0x50, 0x44, 0x46});
    DocumentProcessorClient client = new DocumentProcessorClient(
        new DocumentProcessorProperties("http://127.0.0.1:1", true, 2));

    assertTrue(!client.isReachable());
    assertThrows(ResumeParseException.class,
        () -> client.processPdf(file, "r.pdf", "deadbeef"));
  }

  private static HttpServer stubServer(String path, int status, String body) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/health", exchange -> {
      byte[] ok = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, ok.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(ok);
      }
    });
    server.createContext(path, exchange -> {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    });
    server.start();
    return server;
  }
}
