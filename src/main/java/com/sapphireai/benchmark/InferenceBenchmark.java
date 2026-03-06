package com.sapphireai.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ollama4j.OllamaAPI;
import lombok.Builder;
import lombok.Data;

import java.io.File;
import java.util.*;

/**
 * Standalone benchmark - no Spring context needed
 * Run: mvn test -Dtest=InferenceBenchmark
 */
public class InferenceBenchmark {

    private static final String OLLAMA_URL = "http://localhost:11434";
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        System.out.println("🚀 Starting Inference Benchmark\n");

        List<BenchmarkQuery> queries = loadQueries();
        List<BenchmarkResult> results = new ArrayList<>();

        // Test Model 1: Phi-3 Mini (Fast CPU)
        if (isOllamaAvailable()) {
            System.out.println("=== Testing:  mistral:7b-instruct-q4_0 (CPU) ===");
            results.addAll(testModel("mistral:7b-instruct-q4_0", queries, 30));
        }

        // Test Model 2: Mistral 7B q4 (Quality)
        // Uncomment when you pull this model
        // System.out.println("\n=== Testing: mistral:7b-instruct-q4_0 ===");
        // results.addAll(testModel("mistral:7b-instruct-q4_0", queries, 120));

        // Save results
        String filename = "benchmark-results-" + System.currentTimeMillis() + ".json";
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File(filename), results);

        System.out.println("\n✅ Results saved to: " + filename);

        // Print summary
        printSummary(results);
    }

    private static List<BenchmarkResult> testModel(
            String model,
            List<BenchmarkQuery> queries,
            int timeoutSeconds
    ) throws Exception {

        OllamaAPI client = new OllamaAPI(OLLAMA_URL);
        client.setRequestTimeoutSeconds(timeoutSeconds);

        List<BenchmarkResult> results = new ArrayList<>();

        for (BenchmarkQuery query : queries) {
            System.out.printf("  [%s] Testing... ", query.getId());

            long startTime = System.currentTimeMillis();

            try {
                String prompt = buildPrompt(query.getQuery());

                Map<String, Object> options = new HashMap<>();
                options.put("temperature", 0.7);
                options.put("num_predict", 150);

                var response = client.generate(
                        model,
                        prompt,
                        false,
                        new io.github.ollama4j.utils.Options(options)
                );

                long latency = System.currentTimeMillis() - startTime;

                results.add(BenchmarkResult.builder()
                        .queryId(query.getId())
                        .model(model)
                        .queryText(query.getQuery())
                        .answer(response.getResponse())
                        .latencyMs(latency)
                        .inputTokens(estimateTokens(prompt))
                        .outputTokens(estimateTokens(response.getResponse()))
                        .success(true)
                        .timestamp(System.currentTimeMillis())
                        .build());

                System.out.printf("%dms ✅\n", latency);

            } catch (Exception e) {
                long latency = System.currentTimeMillis() - startTime;

                results.add(BenchmarkResult.builder()
                        .queryId(query.getId())
                        .model(model)
                        .queryText(query.getQuery())
                        .answer(null)
                        .latencyMs(latency)
                        .success(false)
                        .errorMessage(e.getMessage())
                        .timestamp(System.currentTimeMillis())
                        .build());

                System.out.printf("%dms ❌ %s\n", latency, e.getMessage());
            }
        }

        return results;
    }

    private static String buildPrompt(String query) {
        return String.format("""
            Answer this question briefly based on company vacation policy.
            
            Question: %s
            
            Answer:
            """, query);
    }

    private static List<BenchmarkQuery> loadQueries() {
        // Hardcoded queries (no file needed)
        return List.of(
                new BenchmarkQuery("q1", "How many vacation days do employees get?", "FACTUAL", "SIMPLE"),
                new BenchmarkQuery("q2", "What is the process for requesting time off?", "PROCEDURAL", "MEDIUM"),
                new BenchmarkQuery("q3", "Can unused vacation days be carried over?", "FACTUAL", "SIMPLE"),
                new BenchmarkQuery("q4", "Compare vacation policies for full-time vs part-time employees", "COMPARISON", "MEDIUM"),
                new BenchmarkQuery("q5", "Summarize the entire vacation policy", "SUMMARY", "COMPLEX")
        );
    }

    private static boolean isOllamaAvailable() {
        try {
            new OllamaAPI(OLLAMA_URL).listModels();
            return true;
        } catch (Exception e) {
            System.err.println("❌ Ollama not available: " + e.getMessage());
            return false;
        }
    }

    private static int estimateTokens(String text) {
        return text.length() / 4;
    }

    private static void printSummary(List<BenchmarkResult> results) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📊 BENCHMARK SUMMARY");
        System.out.println("=".repeat(80));

        Map<String, List<BenchmarkResult>> byModel = new HashMap<>();
        for (BenchmarkResult r : results) {
            byModel.computeIfAbsent(r.getModel(), k -> new ArrayList<>()).add(r);
        }

        for (Map.Entry<String, List<BenchmarkResult>> entry : byModel.entrySet()) {
            List<BenchmarkResult> modelResults = entry.getValue();

            double avgLatency = modelResults.stream()
                    .mapToLong(BenchmarkResult::getLatencyMs)
                    .average()
                    .orElse(0);

            long successCount = modelResults.stream()
                    .filter(BenchmarkResult::isSuccess)
                    .count();

            long minLatency = modelResults.stream()
                    .mapToLong(BenchmarkResult::getLatencyMs)
                    .min()
                    .orElse(0);

            long maxLatency = modelResults.stream()
                    .mapToLong(BenchmarkResult::getLatencyMs)
                    .max()
                    .orElse(0);

            System.out.printf("\n%s:\n", entry.getKey());
            System.out.printf("  Avg Latency: %.0f ms (min: %d, max: %d)\n", avgLatency, minLatency, maxLatency);
            System.out.printf("  Success Rate: %d/%d (%.0f%%)\n",
                    successCount,
                    modelResults.size(),
                    (successCount * 100.0 / modelResults.size()));
            System.out.printf("  Cost per query: ₹0.00 (CPU-only)\n");
        }

        System.out.println("\n" + "=".repeat(80));
    }

    @Data
    static class BenchmarkQuery {
        private final String id;
        private final String query;
        private final String type;
        private final String complexity;
    }

    @Data
    @Builder
    static class BenchmarkResult {
        private String queryId;
        private String model;
        private String queryText;
        private String answer;
        private long latencyMs;
        private int inputTokens;
        private int outputTokens;
        private boolean success;
        private String errorMessage;
        private long timestamp;
    }
}