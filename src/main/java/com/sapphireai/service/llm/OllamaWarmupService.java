package com.sapphireai.service.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OllamaWarmupService {

    private final InferenceRouter router;

    @EventListener(ApplicationReadyEvent.class)
    public void warmupModels() {
        log.info("🔥 Warming up LLM models...");

        try {
            InferenceRequest request = InferenceRequest.builder()
                    .prompt("Hello, respond with one word.")
                    .maxTokens(5)
                    .temperature(0.7)
                    .timeoutSeconds(60)
                    .build();

            InferenceResponse response = router.generate(
                    request,
                    InferenceRouter.InferenceTier.FAST
            );

            if (response.isSuccess()) {
                log.info("✅ Model warmed up in {}ms", response.getLatencyMs());
            } else {
                log.warn("⚠️ Warmup failed, first query will be slow");
            }

        } catch (Exception e) {
            log.warn("⚠️ Warmup failed: {}", e.getMessage());
        }
    }
}