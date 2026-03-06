package com.sapphireai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Validates critical configuration on startup.
 * Fails fast if required environment variables are missing.
 */
@Component
@Slf4j
public class ConfigurationValidator {

    @Value("${sapphire.inference.remote.enabled:false}")
    private boolean remoteEnabled;

    @Value("${sapphire.inference.remote.api-key:}")
    private String remoteApiKey;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @EventListener(ApplicationReadyEvent.class)
    public void validateConfiguration() {
        log.info("Validating configuration for profile: {}", activeProfile);

        // Validate remote LLM configuration if enabled
        if (remoteEnabled && (remoteApiKey == null || remoteApiKey.isEmpty())) {
            log.error("CONFIGURATION ERROR: Remote LLM is enabled but API key is missing!");
            log.error("Set environment variable: REMOTE_LLM_API_KEY or GROQ_API_KEY");
            throw new IllegalStateException(
                    "Remote LLM enabled but no API key configured. " +
                            "Set REMOTE_LLM_API_KEY or GROQ_API_KEY environment variable."
            );
        }

        // Validate production profile requirements
        if ("prod".equals(activeProfile)) {
            validateProductionConfig();
        }

        log.info("✓ Configuration validation passed");
    }

    private void validateProductionConfig() {
        // Add production-specific validations here
        log.info("Validating production configuration...");
    }
}