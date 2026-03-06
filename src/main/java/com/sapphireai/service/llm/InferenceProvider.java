package com.sapphireai.service.llm;

public interface InferenceProvider {

    /**
     * Generate answer from prompt
     */
    InferenceResponse generate(InferenceRequest request);

    /**
     * Provider metadata
     */
    String getName();
    int getMaxTokens();
    boolean isAvailable();

    /**
     * Cost estimation (optional but critical for enterprise)
     */
    double estimateCostPerQuery(int inputTokens, int outputTokens);
}