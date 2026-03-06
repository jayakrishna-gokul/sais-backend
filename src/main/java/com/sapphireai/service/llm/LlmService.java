package com.sapphireai.service.llm;

import com.sapphireai.model.entity.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LlmService {

    private final InferenceRouter router;

    private String strategy;

    @Autowired
    public LlmService(InferenceRouter router, @Value("${sapphire.inference.strategy:fast-first}") String strategy) {
        this.router = router;
        this.strategy = strategy;
        log.info("LlmService initialized with strategy: {}", strategy);
    }

    /**
     * Generate answer from query and context chunks.
     * Routes to appropriate provider based on strategy.
     */
    public String generateAnswer(String query, List<DocumentChunk> contextChunks) {
        try {
            String context = buildContext(contextChunks);
            String prompt = buildPrompt(query, context);

            // Build inference request
            InferenceRequest request = InferenceRequest.builder()
                    .prompt(prompt)
                    .maxTokens(500)
                    .temperature(0.7)
                    .timeoutSeconds(100)
                    .build();

            // Determine tier based on strategy
            InferenceRouter.InferenceTier tier = selectTier(strategy);

            log.debug("Generating answer with tier: {}", tier);
            long startTime = System.currentTimeMillis();

            // Route to provider
            InferenceResponse response = router.generate(request, tier);

            long totalLatency = System.currentTimeMillis() - startTime;

            if (!response.isSuccess()) {
                log.error("Inference failed: {}", response.getErrorMessage());
                return "I apologize, but I encountered an error generating a response. Please try again.";
            }

            log.info("Answer generated: provider={}, latency={}ms, cost=${:.4f}",
                    response.getProviderName(),
                    totalLatency,
                    response.getInputTokens() > 0
                            ? estimateCost(response)
                            : 0.0
            );

            return response.getAnswer();

        } catch (Exception e) {
            log.error("LLM generation failed", e);
            return "I apologize, but I encountered an error generating a response. Please try again.";
        }
    }

    private InferenceRouter.InferenceTier selectTier(String strategy) {
        return switch (strategy.toLowerCase()) {
            case "fast", "fast-first" -> InferenceRouter.InferenceTier.FAST;
            case "quality", "quality-first" -> InferenceRouter.InferenceTier.QUALITY;
            case "remote", "external" -> InferenceRouter.InferenceTier.EXTERNAL;
            default -> {
                log.warn("Unknown strategy '{}', defaulting to FAST", strategy);
                yield InferenceRouter.InferenceTier.FAST;
            }
        };
    }

    private String buildContext(List<DocumentChunk> chunks) {
        return chunks.stream()
                .map(chunk -> String.format(
                        "[Source: %s]\n%s\n",
                        chunk.getDocumentId(),
                        chunk.getContent()
                ))
                .collect(Collectors.joining("\n---\n\n"));
    }

    private String buildPrompt(String query, String context) {
        return String.format("""
                You are Sapphire AI, an intelligent assistant that answers questions based on company documents.
                
                INSTRUCTIONS:
                1. Answer the question using ONLY the information provided in the context below
                2. If the context doesn't contain enough information, say "I don't have enough information to answer that"
                3. Cite sources by mentioning the document ID when making specific claims
                4. Be concise but complete
                5. Use professional, clear language
                
                CONTEXT FROM COMPANY DOCUMENTS:
                %s
                
                QUESTION:
                %s
                
                ANSWER:
                """, context, query);
    }

    private double estimateCost(InferenceResponse response) {
        // This would be injected from provider
        // For now, rough estimate
        return (response.getInputTokens() + response.getOutputTokens()) / 1000.0 * 0.0002;
    }
}