package com.sapphireai.service.llm;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InferenceResponse {

    // Result
    private String answer;

    // Metadata (for benchmarking)
    private String providerName;
    private long latencyMs;

    // Token tracking (for cost calculation)
    private int inputTokens;
    private int outputTokens;

    // Error handling
    private boolean success;
    private String errorMessage;
}