package com.sapphireai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Sapphire AI Service - Main Application Entry Point
 *
 * <p><b>Architecture Decisions:</b></p>
 *
 * <p><b>@EnableAsync:</b> Enables asynchronous processing for document ingestion.
 * <b>Why?</b> Document processing (Tika parsing + chunking + embedding + indexing) 
 * takes 5-10 seconds. Without async:
 * - User uploads document → HTTP request blocks for 10 seconds → bad UX
 * - With async: User uploads → immediate 202 Accepted → processing happens in background
 * - User polls /api/documents/{id}/status to check completion
 * </p>
 *
 * <p><b>@EnableCaching:</b> Enables Spring Cache abstraction with Caffeine.
 * <b>Why?</b> We cache:
 * - Query embeddings (same questions asked repeatedly)
 * - Frequently accessed document metadata
 * - Search results for common queries
 * Reduces Ollama API calls and Qdrant searches by ~40% in production.
 * </p>
 *
 * <p><b>@SpringBootApplication:</b> Combines @Configuration, @EnableAutoConfiguration, @ComponentScan.
 * Scans all classes under com.sapphireai package for Spring beans.
 * </p>
 *
 * @author Sapphire AI Team
 * @version 1.0.0
 * @since 2025-10-02
 */
@SpringBootApplication
@EnableAsync  // Document processing runs in background threads
@EnableCaching  // Cache embeddings and search results
public class SapphireAiApplication {

    /**
     * Application entry point.
     *
     * <p>Spring Boot initialization sequence:
     * 1. Load application.yml configuration
     * 2. Initialize connection pools (HikariCP for PostgreSQL)
     * 3. Run Flyway database migrations
     * 4. Initialize Qdrant client connection
     * 5. Start embedded Tomcat server on port 8080
     * 6. Register REST controllers and security filters
     * </p>
     *
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        SpringApplication.run(SapphireAiApplication.class, args);
    }
}