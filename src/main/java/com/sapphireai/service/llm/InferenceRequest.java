package com.sapphireai.service.llm;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InferenceRequest {

    // Core
    private String prompt;
    private int maxTokens;
    private double temperature;

    // Metadata (for routing/logging)
    private String organizationId;
    private String userId;
    private String queryId;

    // Timeout (critical for enterprise)
    private int timeoutSeconds;
}