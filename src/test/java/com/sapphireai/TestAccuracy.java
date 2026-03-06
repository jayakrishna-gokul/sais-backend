package com.sapphireai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.util.*;

/**
 * Automated accuracy testing for RAG system
 * Tests that page titles can retrieve their own pages
 */
public class TestAccuracy {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String AUTH_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0QHNhcHBoaXJlYWkuY29tIiwiaWF0IjoxNzY5NDMzNzcwLCJleHAiOjE3Njk1MjAxNzB9._ILgpqPX-SKnm44Sx0nTUk4SrTaVmgfJaJOuoT3mXWY"; // Update after login
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/sapphireai";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgres";
    private static final String CONNECTION_ID = "1388fd3e-0705-4adb-b138-4e68ba62eeb4"; // Your Atlassian connection

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("      RAG ACCURACY TEST SUITE");
        System.out.println("========================================\n");

        // Step 1: Get test cases from database
        List<TestCase> testCases = generateTestCasesFromDB();

        System.out.println("Generated " + testCases.size() + " test cases from synced pages\n");

        if (testCases.isEmpty()) {
            System.out.println("❌ No test cases found. Make sure pages are synced.");
            return;
        }

        // Step 2: Run tests
        int total = Math.min(testCases.size(), 100); // Test first 100 pages
        int correct = 0;
        int wrongSource = 0;
        int notFound = 0;
        int hallucinated = 0;

        List<Integer> ranks = new ArrayList<>();
        List<FailedTest> failures = new ArrayList<>();

        System.out.println("Running " + total + " accuracy tests...\n");

        for (int i = 0; i < total; i++) {
            TestCase test = testCases.get(i);

            System.out.print("Test " + (i+1) + "/" + total + ": \"" + truncate(test.title, 50) + "\" ... ");

            try {
                // Execute search
                SearchResult result = search(test.title);

                if (result.sources.isEmpty()) {
                    System.out.println("❌ NO RESULTS");
                    notFound++;
                    failures.add(new FailedTest(test, "No results returned", null));
                    continue;
                }

                // Check if correct page is in results
                int rank = findPageRank(result.sources, test.pageId);

                if (rank == -1) {
                    System.out.println("❌ WRONG SOURCE (expected page not in results)");
                    wrongSource++;
                    failures.add(new FailedTest(test, "Expected page not found",
                            result.sources.get(0).documentId));
                } else if (rank <= 3) {
                    System.out.println("✅ CORRECT (rank " + rank + ")");
                    correct++;
                    ranks.add(rank);
                } else {
                    System.out.println("⚠️  FOUND BUT LOW RANK (" + rank + ")");
                    wrongSource++;
                    ranks.add(rank);
                    failures.add(new FailedTest(test, "Page found but at rank " + rank,
                            result.sources.get(0).documentId));
                }

                // Check for hallucination
                if (result.answer != null && !containsPageTitle(result.answer, test.title)) {
                    System.out.println("    ⚠️  Possible hallucination detected");
                    hallucinated++;
                }

            } catch (Exception e) {
                System.out.println("❌ ERROR: " + e.getMessage());
                notFound++;
                failures.add(new FailedTest(test, "Exception: " + e.getMessage(), null));
            }

            // Small delay to avoid overwhelming the system
            Thread.sleep(100);
        }

        // Step 3: Calculate metrics
        double accuracy = (correct * 100.0) / total;
        double mrr = calculateMRR(ranks);

        // Step 4: Print report
        System.out.println("\n========================================");
        System.out.println("              RESULTS");
        System.out.println("========================================");
        System.out.println("Total Tests: " + total);
        System.out.println("✅ Correct (in top 3): " + correct + " (" + String.format("%.2f", accuracy) + "%)");
        System.out.println("❌ Wrong Source: " + wrongSource + " (" + String.format("%.2f", (wrongSource*100.0/total)) + "%)");
        System.out.println("🚫 Not Found: " + notFound + " (" + String.format("%.2f", (notFound*100.0/total)) + "%)");
        System.out.println("⚠️  Possible Hallucinations: " + hallucinated + " (" + String.format("%.2f", (hallucinated*100.0/total)) + "%)");
        System.out.println("\n📊 Mean Reciprocal Rank (MRR): " + String.format("%.4f", mrr));
        System.out.println("📈 Average Rank of Correct Results: " + String.format("%.2f",
                ranks.isEmpty() ? 0 : ranks.stream().mapToInt(Integer::intValue).average().orElse(0)));

        System.out.println("\n🎯 FINAL ACCURACY: " + String.format("%.2f", accuracy) + "%");

        if (accuracy >= 90) {
            System.out.println("🏆 EXCELLENT - Production Ready!");
        } else if (accuracy >= 80) {
            System.out.println("✅ GOOD - Minor improvements needed");
        } else if (accuracy >= 70) {
            System.out.println("⚠️  ACCEPTABLE - Needs optimization");
        } else {
            System.out.println("❌ POOR - Major issues need fixing");
        }

        System.out.println("========================================");

        // Show some failures
        if (!failures.isEmpty()) {
            System.out.println("\n📋 Sample Failures (first 10):");
            failures.stream().limit(10).forEach(f -> {
                System.out.println("\n---");
                System.out.println("Query: " + f.test.title);
                System.out.println("Expected Page: " + f.test.pageId);
                System.out.println("Issue: " + f.reason);
                if (f.actualTopResult != null) {
                    System.out.println("Got Instead: " + f.actualTopResult);
                }
            });
        }

        System.out.println("\n========================================");
    }

    /**
     * Generate test cases from synced Confluence pages
     */
    private static List<TestCase> generateTestCasesFromDB() throws SQLException {
        List<TestCase> testCases = new ArrayList<>();

        String sql = """
        SELECT cp.page_id, cp.title 
        FROM confluence_page cp
        INNER JOIN documents d 
            ON d.id = CONCAT('confluence_', cp.connection_id, '_', cp.page_id)
        WHERE cp.connection_id = ? 
        AND cp.title IS NOT NULL 
        AND cp.title != ''
        ORDER BY cp.last_synced_at DESC
        """;

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, CONNECTION_ID);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String pageId = rs.getString("page_id");
                    String title = rs.getString("title");

                    // Filter out templates and generic pages
                    if (!title.toLowerCase().contains("template") &&
                            !title.startsWith("_") &&
                            title.length() > 5) {
                        testCases.add(new TestCase(pageId, title));
                    }
                }
            }
        }

        return testCases;
    }

    /**
     * Execute search via API
     */
    private static SearchResult search(String query) throws Exception {
        String json = String.format("""
            {
                "query": "%s",
                "generateAnswer": true,
                "maxResults": 10
            }
            """, query.replace("\"", "\\\""));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/search"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + AUTH_TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Search failed: " + response.statusCode());
        }

        Map<String, Object> result = mapper.readValue(response.body(), Map.class);

        String answer = (String) result.get("answer");
        List<Map<String, Object>> sources = (List<Map<String, Object>>) result.get("sources");

        List<Source> sourceDtos = new ArrayList<>();
        if (sources != null) {
            for (Map<String, Object> s : sources) {
                sourceDtos.add(new Source(
                        (String) s.get("documentId"),
                        (String) s.get("content")
                ));
            }
        }

        return new SearchResult(answer, sourceDtos);
    }

    /**
     * Find rank of correct page in results (1-indexed)
     */
    private static int findPageRank(List<Source> sources, String expectedPageId) {
        for (int i = 0; i < sources.size(); i++) {
            String docId = sources.get(i).documentId;
            // Check if document ID contains the page ID
            if (docId != null && docId.contains("_" + expectedPageId)) {
                return i + 1; // 1-indexed rank
            }
        }
        return -1; // Not found
    }

    /**
     * Check if answer contains reasonable content from page title
     */
    private static boolean containsPageTitle(String answer, String title) {
        if (answer == null || title == null) return false;

        String lowerAnswer = answer.toLowerCase();
        String lowerTitle = title.toLowerCase();

        // Check if main keywords from title are in answer
        String[] titleWords = lowerTitle.split("\\s+");
        long matchCount = Arrays.stream(titleWords)
                .filter(word -> word.length() > 3) // Skip short words
                .filter(lowerAnswer::contains)
                .count();

        // At least 50% of significant words should be in answer
        long significantWords = Arrays.stream(titleWords)
                .filter(word -> word.length() > 3)
                .count();

        return significantWords > 0 && matchCount >= (significantWords * 0.5);
    }

    /**
     * Calculate Mean Reciprocal Rank
     */
    private static double calculateMRR(List<Integer> ranks) {
        if (ranks.isEmpty()) return 0.0;

        return ranks.stream()
                .mapToDouble(rank -> 1.0 / rank)
                .average()
                .orElse(0.0);
    }

    private static String truncate(String str, int length) {
        if (str == null) return "null";
        return str.length() > length ? str.substring(0, length) + "..." : str;
    }

    @Data
    static class TestCase {
        final String pageId;
        final String title;
    }

    @Data
    static class SearchResult {
        final String answer;
        final List<Source> sources;
    }

    @Data
    static class Source {
        final String documentId;
        final String content;
    }

    @Data
    static class FailedTest {
        final TestCase test;
        final String reason;
        final String actualTopResult;
    }
}