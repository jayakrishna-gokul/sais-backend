package com.sapphireai.controller;

import com.sapphireai.service.embedding.EmbeddingService;
import com.sapphireai.service.llm.InferenceRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Health check endpoint for monitoring.
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final EmbeddingService embeddingService;
    private final InferenceRouter inferenceRouter;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();

        health.put("status", "UP");
        health.put("embedding", Map.of(
                "provider", embeddingService.getActiveModel(),
                "dimension", embeddingService.getEmbeddingDimension()
        ));

        // Check if any inference provider is available
        boolean inferenceAvailable = !inferenceRouter.getAvailableProviders().isEmpty();
        health.put("inference", Map.of(
                "available", inferenceAvailable
        ));

        return ResponseEntity.ok(health);
    }
}