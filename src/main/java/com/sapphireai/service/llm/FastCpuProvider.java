package com.sapphireai.service.llm;

import io.github.ollama4j.OllamaAPI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "sapphire.inference.fast.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class FastCpuProvider implements InferenceProvider {

    private final OllamaAPI client;
    private final String model;

    public FastCpuProvider(
            @Value("${sapphire.llm.base-url}") String ollamaUrl,
            @Value("${sapphire.inference.fast.model:phi3:mini}") String model
    ) {
        this.client = new OllamaAPI(ollamaUrl);
        this.client.setRequestTimeoutSeconds(180000); // Fast = strict timeout
        this.model = model;
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
            log.error("Fast provider failed", e);
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
        return "fast-cpu:" + model;
    }

    @Override
    public int getMaxTokens() {
        return 2048;
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
        return 0.0; // Free (CPU)
    }

    private int estimateTokens(String text) {
        return text.length() / 4;
    }
}