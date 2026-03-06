package com.sapphireai.service.embedding;

/**
 * Embedding generation abstraction.
 *
 * Implementations:
 * - HuggingFaceEmbedder: In-process, fast (10ms), 384 dimensions
 * - OllamaEmbedder: HTTP, reliable (50ms), 768 dimensions
 */
public interface Embedder {

    /**
     * Generate embedding vector for text.
     * @param text Input text
     * @return Embedding vector
     */
    float[] embed(String text);

    /**
     * Get embedding dimension.
     * Critical: Must match Qdrant collection vector size.
     */
    int getDimension();

    /**
     * Get model identifier.
     */
    String getModelName();

    /**
     * Check if embedder is available.
     */
    boolean isAvailable();
}