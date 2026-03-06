package com.sapphireai.service.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class InferenceRouter {

    private final List<InferenceProvider> providers;

    @Autowired
    public InferenceRouter(List<InferenceProvider> providers) {
        this.providers = providers;
        log.info("Loaded {} inference providers: {}",
                providers.size(),
                providers.stream().map(InferenceProvider::getName).toList());
    }

    /**
     * Route to appropriate provider based on request
     */
    public InferenceResponse generate(InferenceRequest request, InferenceTier tier) {
        InferenceProvider provider = selectProvider(tier);

        if (provider == null || !provider.isAvailable()) {
            log.warn("Requested provider not available, falling back");
            provider = providers.get(0); // Fallback to first available
        }

        log.info("Routing to provider: {}", provider.getName());
        return provider.generate(request);
    }

    private InferenceProvider selectProvider(InferenceTier tier) {
        return switch (tier) {
            case FAST -> providers.stream()
                    .filter(p -> p.getName().startsWith("fast"))
                    .findFirst()
                    .orElse(null);
            case QUALITY -> providers.stream()
                    .filter(p -> p.getName().startsWith("quality"))
                    .findFirst()
                    .orElse(null);
            case EXTERNAL -> providers.stream()
                    .filter(p -> p.getName().startsWith("remote") || p.getName().startsWith("external"))
                    .findFirst()
                    .orElse(null);
        };
    }

    public enum InferenceTier {
        FAST,
        QUALITY,
        EXTERNAL
    }

    public List<String> getAvailableProviders() {
        return providers.stream()
                .filter(InferenceProvider::isAvailable)
                .map(InferenceProvider::getName)
                .toList();
    }
}