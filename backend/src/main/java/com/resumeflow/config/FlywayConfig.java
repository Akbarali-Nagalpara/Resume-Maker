package com.resumeflow.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Manual Flyway setup (Spring Boot 4 ships no Flyway auto-configuration).
 * Migrations run before Hibernate validates the schema.
 */
@Configuration
public class FlywayConfig {

  @Bean(initMethod = "migrate")
  public Flyway flyway(DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load();
  }

  /**
   * Orders the JPA {@code EntityManagerFactory} after the {@code flyway} bean
   * so schema validation only runs against migrated schemas.
   */
  @Bean
  public static BeanFactoryPostProcessor entityManagerFactoryDependsOnFlyway() {
    return beanFactory -> {
      if (beanFactory.containsBeanDefinition("entityManagerFactory")) {
        BeanDefinition definition = beanFactory.getBeanDefinition("entityManagerFactory");
        String[] existing =
            definition.getDependsOn() == null ? new String[0] : definition.getDependsOn();
        String[] merged = new String[existing.length + 1];
        System.arraycopy(existing, 0, merged, 0, existing.length);
        merged[existing.length] = "flyway";
        definition.setDependsOn(merged);
      }
    };
  }
}
