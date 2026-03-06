package com.sapphireai.service.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Universal external LLM provider supporting OpenAI-compatible APIs.
 * Works with: OpenAI, Groq, Together.ai, Anthropic (via proxy), etc.
 *
 * Configuration:
 * - sapphire.inference.external.api-url: Base API URL
 * - sapphire.inference.external.api-key: API key
 * - sapphire.inference.external.model: Model name
 */
@Component
@ConditionalOnProperty(name = "sapphire.inference.external.enabled", havingValue = "true")
@Slf4j
public class ExternalLlmProvider_OBSELETE implements InferenceProvider {

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String apiKey;
    private final String model;

    public ExternalLlmProvider_OBSELETE(
            @Value("${sapphire.inference.external.api-url}") String apiUrl,
            @Value("${sapphire.inference.external.api-key}") String apiKey,
            @Value("${sapphire.inference.external.model:gpt-4o-mini}") String model
    ) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.restTemplate = new RestTemplate();

        log.info("ExternalLlmProvider_OBSELETE initialized: url={}, model={}", apiUrl, model);
    }

    @Override
    public InferenceResponse generate(InferenceRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // Build OpenAI-compatible request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", request.getPrompt())
            ));
            requestBody.put("temperature", request.getTemperature());
            requestBody.put("max_tokens", request.getMaxTokens());

            // Build headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Make API call (OpenAI-compatible format)
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl + "/v1/chat/completions",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            long latency = System.currentTimeMillis() - startTime;

            // Parse response
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("choices")) {
                var choices = (List<Map<String, Object>>) body.get("choices");
                if (choices.isEmpty()) {
                    throw new RuntimeException("Empty choices array from external LLM");
                }

                var message = (Map<String, Object>) choices.get(0).get("message");
                String answer = (String) message.get("content");

                // Parse usage (token counts)
                int inputTokens = 0;
                int outputTokens = 0;
                if (body.containsKey("usage")) {
                    var usage = (Map<String, Object>) body.get("usage");
                    inputTokens = ((Number) usage.getOrDefault("prompt_tokens", 0)).intValue();
                    outputTokens = ((Number) usage.getOrDefault("completion_tokens", 0)).intValue();
                }

                log.info("External LLM response: {}ms, input={} tokens, output={} tokens",
                        latency, inputTokens, outputTokens);

                return InferenceResponse.builder()
                        .answer(answer)
                        .providerName(getName())
                        .latencyMs(latency)
                        .inputTokens(inputTokens)
                        .outputTokens(outputTokens)
                        .success(true)
                        .build();
            }

            throw new RuntimeException("Invalid response format from external LLM");

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("External LLM generation failed: {}", e.getMessage(), e);

            return InferenceResponse.builder()
                    .success(false)
                    .errorMessage("External LLM error: " + e.getMessage())
                    .providerName(getName())
                    .latencyMs(latency)
                    .build();
        }
    }

    @Override
    public String getName() {
        return "external:" + model;
    }

    @Override
    public int getMaxTokens() {
        // Most OpenAI-compatible APIs support 4096+
        // Can be made configurable if needed
        return 8192;
    }

    @Override
    public boolean isAvailable() {
        try {
            // Simple health check - try to list models
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl + "/v1/models",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            boolean available = response.getStatusCode().is2xxSuccessful();
            log.debug("External LLM availability check: {}", available);
            return available;

        } catch (Exception e) {
            log.warn("External LLM not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public double estimateCostPerQuery(int inputTokens, int outputTokens) {
        // Provider-specific pricing
        // This is a rough estimate - actual costs vary by provider

        if (apiUrl.contains("groq.com")) {
            // Groq is free (within rate limits)
            return 0.0;

        } else if (apiUrl.contains("together.xyz")) {
            // Together.ai: ~$0.0002 per 1K tokens
            return ((inputTokens + outputTokens) / 1000.0) * 0.0002;

        } else if (apiUrl.contains("openai.com")) {
            // GPT-4o-mini: $0.150/1M input, $0.600/1M output
            double inputCost = (inputTokens / 1_000_000.0) * 0.15;
            double outputCost = (outputTokens / 1_000_000.0) * 0.60;
            return inputCost + outputCost;

        } else {
            // Unknown provider - estimate
            return ((inputTokens + outputTokens) / 1000.0) * 0.001;
        }
    }
}