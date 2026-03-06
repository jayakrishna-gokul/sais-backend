package com.sapphireai.service.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Ollama embeddings via HTTP API.
 *
 * CHARACTERISTICS:
 * - Latency: 50ms per embedding
 * - Model: nomic-embed-text (768 dimensions)
 * - Requirements: Ollama server running
 * - Use case: Fallback when HuggingFace unavailable
 */
@Component
@Slf4j
public class OllamaEmbedder implements Embedder {

    private final WebClient webClient;
    private final String model;
    private final int dimension;

    public OllamaEmbedder(
            @Value("${sapphire.llm.base-url}") String ollamaUrl,
            @Value("${sapphire.embedding.ollama.model:nomic-embed-text}") String model,
            @Value("${sapphire.embedding.ollama.dimension:768}") int dimension
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(ollamaUrl)
                .build();
        this.model = model;
        this.dimension = dimension;

        log.info("OllamaEmbedder initialized: model={}, dimension={}, url={}",
                model, dimension, ollamaUrl);
    }

    @Override
    public float[] embed(String text) {
        try {
            Map<String, Object> request = Map.of(
                    "model", model,
                    "prompt", text
            );

            Map<String, Object> response = webClient.post()
                    .uri("/api/embeddings")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null || !response.containsKey("embedding")) {
                throw new RuntimeException("Invalid Ollama response");
            }

            @SuppressWarnings("unchecked")
            List<Double> embedding = (List<Double>) response.get("embedding");

            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).floatValue();
            }

            return result;

        } catch (Exception e) {
            log.error("Ollama embedding failed", e);
            throw new RuntimeException("Ollama embedding failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public String getModelName() {
        return "ollama:" + model;
    }

    @Override
    public boolean isAvailable() {
        try {
            webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return true;
        } catch (Exception e) {
            log.debug("Ollama not available: {}", e.getMessage());
            return false;
        }
    }
}