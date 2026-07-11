package com.markit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the markit backend — a modular monolith (ADR-0001).
 *
 * <p>Bounded contexts live as packages under {@code com.markit}: {@code identity},
 * {@code bookmarking}, {@code scraping}, {@code search}, with cross-cutting infrastructure under
 * {@code shared}. Each context follows {@code domain / application / infrastructure / presentation}
 * layering; the {@code domain} layer stays framework-free (enforced by ArchUnit, NFR-MAINT-001).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MarkitApplication {

  public static void main(String[] args) {
    SpringApplication.run(MarkitApplication.class, args);
  }
}
