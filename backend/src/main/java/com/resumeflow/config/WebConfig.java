package com.resumeflow.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the ResumeFlow frontend (Vite dev server and deployed origins).
 * Restricted to {@code /api/**}; the download filename header is exposed so
 * the frontend can save artifacts with their server-side names.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final List<String> allowedOrigins;

  public WebConfig(
      @Value("${resumeflow.cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://localhost:8080}")
          String[] allowedOrigins) {
    this.allowedOrigins = Arrays.asList(allowedOrigins);
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOrigins(allowedOrigins.toArray(new String[0]))
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .exposedHeaders(HttpHeaders.CONTENT_DISPOSITION)
        .allowCredentials(false)
        .maxAge(3600);
  }
}
