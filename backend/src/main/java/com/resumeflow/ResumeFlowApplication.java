package com.resumeflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ConfigurationPropertiesScan("com.resumeflow.config")
@EnableJpaRepositories("com.resumeflow.resume.repository")
public class ResumeFlowApplication {

  public static void main(String[] args) {
    SpringApplication.run(ResumeFlowApplication.class, args);
  }
}
