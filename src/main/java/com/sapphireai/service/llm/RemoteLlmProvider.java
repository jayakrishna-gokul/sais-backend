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
 * Universal remote LLM provider supporting OpenAI-compatible APIs.
 * Works with: Groq, Together.ai, OpenAI, Anthropic (via proxy), etc.
 * PERFORMANCE OPTIMIZATION:
 * - Availability check is cached for 5 minutes
 * - Prevents 2-second delay on every search request
 * Configuration:
 * - sapphire.inference.remote.api-url: Base API URL (without /v1/chat/completions)
 * - sapphire.inference.remote.api-key: API key
 * - sapphire.inference.remote.model: Model name
 * - sapphire.inference.remote.endpoint: API endpoint path (default: /v1/chat/completions)
 */
@Component
@ConditionalOnProperty(name = "sapphire.inference.remote.enabled", havingValue = "true")
@Slf4j
public class RemoteLlmProvider implements InferenceProvider {

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final String endpoint;
    private volatile Boolean availabilityCache = null;
    private volatile long lastAvailabilityCheck = 0;
    private static final long AVAILABILITY_CACHE_TTL = 300_000; // 5 minutes

    public RemoteLlmProvider(
            @Value("${sapphire.inference.remote.api-url}") String apiUrl,
            @Value("${sapphire.inference.remote.api-key}") String apiKey,
            @Value("${sapphire.inference.remote.model:llama-3.1-70b-versatile}") String model,
            @Value("${sapphire.inference.remote.endpoint:/v1/chat/completions}") String endpoint
    ) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.endpoint = endpoint;
        this.restTemplate = new RestTemplate();

        log.info("RemoteLlmProvider initialized: url={}, model={}, endpoint={}",
                apiUrl, model, endpoint);
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

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Call API with configurable endpoint
            String fullUrl = apiUrl + endpoint;
            log.debug("Calling remote LLM: {}", fullUrl);

            ResponseEntity<Map> response = restTemplate.exchange(
                    fullUrl,
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
                    throw new RuntimeException("Empty choices array from remote LLM");
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

                log.info("Remote LLM response: {}ms, input={} tokens, output={} tokens",
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

            throw new RuntimeException("Invalid response format from remote LLM");

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Remote LLM generation failed: {}", e.getMessage(), e);

            return InferenceResponse.builder()
                    .success(false)
                    .errorMessage("Remote LLM error: " + e.getMessage())
                    .providerName(getName())
                    .latencyMs(latency)
                    .build();
        }
    }

    @Override
    public String getName() {
        return "remote:" + model;
    }

    @Override
    public int getMaxTokens() {
        return 8192;
    }

    @Override
    public boolean isAvailable() {
        long now = System.currentTimeMillis();

        // Return cached result if recent
        if (availabilityCache != null &&
                (now - lastAvailabilityCheck) < AVAILABILITY_CACHE_TTL) {
            return availabilityCache;
        }

        // Check availability (only every 5 minutes)
        try {
            log.debug("Checking remote LLM availability...");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl + "/v1/models",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            availabilityCache = response.getStatusCode().is2xxSuccessful();
            lastAvailabilityCheck = now;

            log.debug("Remote LLM availability: {}", availabilityCache);
            log.info("Remote LLM availability: {} (cached for {}min)",
                    availabilityCache, AVAILABILITY_CACHE_TTL / 60000);
            return availabilityCache;

        } catch (Exception e) {
            log.warn("Remote LLM availability check failed: {}", e.getMessage());
            availabilityCache = false;
            lastAvailabilityCheck = now;
            return false;
        }
    }

    @Override
    public double estimateCostPerQuery(int inputTokens, int outputTokens) {
        // Provider-specific pricing detection
        String lowerUrl = apiUrl.toLowerCase();

        if (lowerUrl.contains("groq.com")) {
            // Groq is free (within rate limits)
            return 0.0;

        } else if (lowerUrl.contains("together.xyz") || lowerUrl.contains("together.ai")) {
            // Together.ai: ~$0.0002 per 1K tokens = â‚¹0.017 per 1K tokens
            return ((inputTokens + outputTokens) / 1000.0) * 0.017;

        } else if (lowerUrl.contains("openai.com")) {
            // GPT-4o-mini: $0.150/1M input, $0.600/1M output
            double inputCost = (inputTokens / 1_000_000.0) * 0.15;
            double outputCost = (outputTokens / 1_000_000.0) * 0.60;
            return (inputCost + outputCost) * 83; // Convert to INR (approx)

        } else {
            // Unknown provider - estimate
            return ((inputTokens + outputTokens) / 1000.0) * 0.02;
        }
    }
}