package com.sapphireai.service.embedding;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;

/**
 * HuggingFace embeddings using DJL (Deep Java Library).
 *
 * CHARACTERISTICS:
 * - Latency: 10ms per embedding (5x faster than Ollama)
 * - Model: sentence-transformers/all-MiniLM-L6-v2 (384 dimensions)
 * - Requirements: Model files (auto-downloaded ~80MB)
 * - Use case: Primary embedder for production
 *
 * SETUP:
 * Model auto-downloads on first use to ~/.djl.ai/cache/
 */
@Component
@ConditionalOnProperty(name = "sapphire.embedding.huggingface.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class HuggingFaceEmbedder implements Embedder {

    private final String modelName;
    private final int dimension;

    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;
    private boolean initialized = false;

    public HuggingFaceEmbedder(
            @Value("${sapphire.embedding.huggingface.model-name:sentence-transformers/all-MiniLM-L6-v2}") String modelName,
            @Value("${sapphire.embedding.huggingface.dimension:384}") int dimension
    ) {
        this.modelName = modelName;
        this.dimension = dimension;
    }

    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing HuggingFaceEmbedder: model={}", modelName);

            Criteria<String, float[]> criteria = Criteria.builder()
                    .optApplication(Application.NLP.TEXT_EMBEDDING)
                    .setTypes(String.class, float[].class)
                    .optModelUrls("djl://ai.djl.huggingface.pytorch/" + modelName)
                    .optEngine("PyTorch")
                    .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                    .build();

            model = criteria.loadModel();
            predictor = model.newPredictor();
            initialized = true;

            log.info("HuggingFaceEmbedder ready: dimension={}", dimension);

        } catch (ModelNotFoundException | MalformedModelException | IOException e) {
            log.error("Failed to load HuggingFace model: {}", modelName, e);
            initialized = false;
        }
    }

    @Override
    public float[] embed(String text) {
        if (!initialized) {
            throw new RuntimeException("HuggingFace embedder not initialized");
        }

        try {
            return predictor.predict(text);

        } catch (Exception e) {
            log.error("HuggingFace embedding failed", e);
            throw new RuntimeException("HuggingFace embedding failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public String getModelName() {
        return "huggingface:" + modelName;
    }

    @Override
    public boolean isAvailable() {
        return initialized;
    }

    @PreDestroy
    public void cleanup() {
        if (predictor != null) {
            predictor.close();
        }
        if (model != null) {
            model.close();
        }
        log.info("HuggingFaceEmbedder cleaned up");
    }
}