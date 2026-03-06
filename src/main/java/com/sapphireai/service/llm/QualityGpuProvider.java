package com.sapphireai.service.llm;

import io.github.ollama4j.OllamaAPI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "sapphire.inference.quality.enabled", havingValue = "true")
@Slf4j
public class QualityGpuProvider implements InferenceProvider {

    private final OllamaAPI client;
    private final String model;

    public QualityGpuProvider(
            @Value("${sapphire.llm.base-url}") String ollamaUrl,
            @Value("${sapphire.inference.quality.model:mistral:7b-instruct-q4_0}") String model
    ) {
        this.client = new OllamaAPI(ollamaUrl);
        this.client.setRequestTimeoutSeconds(180000); // More time for quality
        this.model = model;
        log.info("QualityGpuProvider initialized: model={}", model);
    }

    @Override
    public InferenceResponse generate(InferenceRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> options = new HashMap<>();
            options.put("temperature", request.getTemperature());
            options.put("num_predict", request.getMaxTokens());

            var result = client.generate(
                    model,
                    request.getPrompt(),
                    false,
                    new io.github.ollama4j.utils.Options(options)
            );

            long latency = System.currentTimeMillis() - startTime;

            return InferenceResponse.builder()
                    .answer(result.getResponse())
                    .providerName(getName())
                    .latencyMs(latency)
                    .inputTokens(estimateTokens(request.getPrompt()))
                    .outputTokens(estimateTokens(result.getResponse()))
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Quality provider failed", e);
            return InferenceResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .providerName(getName())
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    @Override
    public String getName() {
        return "quality-gpu:" + model;
    }

    @Override
    public int getMaxTokens() {
        return 8192;
    }

    @Override
    public boolean isAvailable() {
        try {
            client.listModels();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public double estimateCostPerQuery(int inputTokens, int outputTokens) {
        // If using cloud GPU, calculate cost
        // Example: GCP L4 = ₹65/hour = ₹0.018/sec
        // Average query = 10s = ₹0.18
        return 0.20;
    }

    private int estimateTokens(String text) {
        return text.length() / 4;
    }
}