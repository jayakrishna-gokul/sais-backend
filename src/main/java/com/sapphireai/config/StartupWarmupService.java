package com.sapphireai.config;

import com.sapphireai.service.embedding.EmbeddingService;
import com.sapphireai.service.llm.InferenceRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupWarmupService {

    private final EmbeddingService embeddingService;
    private final InferenceRouter inferenceRouter;

    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        log.info("🔥 Warming up system components...");
        long totalStart = System.currentTimeMillis();

        // 1. Warm up embeddings
        log.info("Loading embedding model...");
        long embStart = System.currentTimeMillis();
        embeddingService.generateEmbedding("System warmup query");
        log.info("✅ Embeddings ready: {}ms", System.currentTimeMillis() - embStart);

        // 2. Warm up LLM (if using local)
        if (!inferenceRouter.getAvailableProviders().isEmpty()) {
            log.info("Checking LLM providers...");
            long llmStart = System.currentTimeMillis();
            inferenceRouter.getAvailableProviders().forEach(provider ->
                    log.info("  - Available: {}", provider)
            );
            log.info("✅ LLM providers ready: {}ms", System.currentTimeMillis() - llmStart);
        }

        long totalTime = System.currentTimeMillis() - totalStart;
        log.info("🎉 System warmup complete: {}ms", totalTime);
        log.info("📊 System is ready for production traffic");
    }
}