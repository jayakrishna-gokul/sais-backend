package com.sapphireai.service.rag;

import com.sapphireai.model.dto.SearchRequest;
import com.sapphireai.model.dto.SearchResponse;
import com.sapphireai.model.dto.SearchResultDto;
import com.sapphireai.model.entity.DocumentChunk;
import com.sapphireai.repository.ChunkRepository;
import com.sapphireai.service.llm.LlmService;
import com.sapphireai.service.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.sapphireai.repository.DocumentRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG orchestration engine.
 * Coordinates: Query → Retrieval → Re-rank → Generate → Validate
 * Target accuracy: 91%
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagEngine {

    private final VectorStoreService vectorStore;
    private final ChunkRepository chunkRepository;
    private final LlmService llmService;
    private final DocumentRepository documentRepository;

    @Value("${sapphire.rag.top-k:20}")
    private int topK;

    @Value("${sapphire.rag.rerank-top-k:5}")
    private int rerankTopK;

    @Value("${sapphire.rag.min-confidence:0.5}")
    private double minConfidence;

    /**
     * Main RAG search pipeline.
     */
    public SearchResponse search(SearchRequest request, String organizationId) {
        long startTime = System.currentTimeMillis();

        try {
            // STEP 1: Vector search (hybrid - vector + BM25)
            List<VectorStoreService.ScoredChunk> vectorResults =
                    vectorStore.search(request.getQuery(), organizationId, topK);

            if (vectorResults.isEmpty()) {
                return buildEmptyResponse(request.getQuery(), startTime);
            }

            // STEP 2: Retrieve full chunks from database
            List<String> chunkIds = vectorResults.stream()
                    .map(VectorStoreService.ScoredChunk::chunkId)
                    .collect(Collectors.toList());


            log.info("Retrieving {} chunk IDs from database: {}", chunkIds.size(), chunkIds);

            List<DocumentChunk> chunks = chunkRepository.findByIdIn(chunkIds);

            log.info("Retrieved {} chunks from database", chunks.size());

            // Set vector scores
            for (DocumentChunk chunk : chunks) {
                vectorResults.stream()
                        .filter(r -> r.chunkId().equals(chunk.getId()))
                        .findFirst()
                        .ifPresent(r -> chunk.setVectorScore((double) r.score()));
            }

            // STEP 3: Re-rank (simple scoring for now, can add cross-encoder later)
            List<DocumentChunk> reranked = rerank(chunks, request.getQuery());
            List<DocumentChunk> topChunks = reranked.subList(0, Math.min(rerankTopK, reranked.size()));

            // STEP 4: Expand context (retrieve parent chunks if available)
            List<DocumentChunk> expandedChunks = expandContext(topChunks);

            // STEP 5: Generate answer (or return chunks only)
            String answer = null;
            if (request.getGenerateAnswer()) {
                answer = llmService.generateAnswer(request.getQuery(), expandedChunks);
            }

            // STEP 6: Calculate confidence
            double confidence = calculateConfidence(topChunks);

            // Build response
            List<SearchResultDto> sources = topChunks.stream()
                    .map(this::toSearchResult)
                    .collect(Collectors.toList());

            long processingTime = System.currentTimeMillis() - startTime;

            log.info("RAG search completed: query='{}', results={}, time={}ms, confidence={}",
                    request.getQuery(), sources.size(), processingTime, confidence);

            return SearchResponse.builder()
                    .query(request.getQuery())
                    .answer(answer)
                    .sources(sources)
                    .confidence(confidence)
                    .sessionId(request.getSessionId())
                    .processingTimeMs(processingTime)
                    .build();

        } catch (Exception e) {
            log.error("RAG search failed", e);
            throw new RuntimeException("Search failed: " + e.getMessage());
        }
    }

    private List<DocumentChunk> rerank(List<DocumentChunk> chunks, String query) {
        // Simple re-ranking: combine vector score with BM25-style keyword matching
        String[] queryTerms = query.toLowerCase().split("\\s+");

        for (DocumentChunk chunk : chunks) {
            double vectorScore = chunk.getVectorScore() != null ? chunk.getVectorScore() : 0.0;
            double keywordScore = calculateKeywordScore(chunk.getContent(), queryTerms);

            // Hybrid scoring: 70% vector, 30% keyword
            double finalScore = (vectorScore * 0.7) + (keywordScore * 0.3);
            chunk.setRelevanceScore(finalScore);
        }

        // Sort by final score
        chunks.sort((a, b) -> Double.compare(
                b.getRelevanceScore() != null ? b.getRelevanceScore() : 0.0,
                a.getRelevanceScore() != null ? a.getRelevanceScore() : 0.0
        ));

        return chunks;
    }

    private double calculateKeywordScore(String content, String[] queryTerms) {
        String lowerContent = content.toLowerCase();
        int matches = 0;

        for (String term : queryTerms) {
            if (lowerContent.contains(term)) {
                matches++;
            }
        }

        return queryTerms.length > 0 ? (double) matches / queryTerms.length : 0.0;
    }

    private List<DocumentChunk> expandContext(List<DocumentChunk> chunks) {
        // For chunks with parent references, retrieve parent for fuller context
        List<DocumentChunk> expanded = chunks.stream()
                .filter(c -> c.getParentChunkId() != null)
                .map(c -> chunkRepository.findById(c.getParentChunkId()).orElse(c))
                .collect(Collectors.toList());

        // If no parents found, return original chunks
        return expanded.isEmpty() ? chunks : expanded;
    }

    private double calculateConfidence(List<DocumentChunk> chunks) {
        if (chunks.isEmpty()) return 0.0;

        // Get the top chunk's score (most relevant result)
        DocumentChunk topChunk = chunks.get(0);
        double topScore = topChunk.getRelevanceScore() != null ? topChunk.getRelevanceScore() : 0.0;

        // Cosine similarity interpretation for semantic search:
        // 0.70+ = Excellent match (near-duplicate content)
        // 0.50+ = Very good match (highly relevant)
        // 0.35+ = Good match (relevant)
        // 0.20+ = Fair match (possibly relevant)
        // Below 0.20 = Poor match

        // Map vector similarity scores to confidence percentages
        // that make sense to end users and clients
        double confidence;

        if (topScore >= 0.70) {
            // Excellent: 90-100% confidence
            confidence = 0.90 + (topScore - 0.70) * 0.33;  // Maps 0.70-1.0 → 90-100%
        } else if (topScore >= 0.50) {
            // Very good: 75-90% confidence
            confidence = 0.75 + (topScore - 0.50) * 0.75;  // Maps 0.50-0.70 → 75-90%
        } else if (topScore >= 0.35) {
            // Good: 60-75% confidence
            confidence = 0.60 + (topScore - 0.35) * 1.0;   // Maps 0.35-0.50 → 60-75%
        } else if (topScore >= 0.20) {
            // Fair: 40-60% confidence
            confidence = 0.40 + (topScore - 0.20) * 1.33;  // Maps 0.20-0.35 → 40-60%
        } else {
            // Poor: 0-40% confidence
            confidence = topScore * 2.0;                   // Maps 0.0-0.20 → 0-40%
        }

        // Boost confidence if multiple chunks agree (consensus)
        if (chunks.size() >= 3) {
            double secondScore = chunks.get(1).getRelevanceScore() != null
                    ? chunks.get(1).getRelevanceScore() : 0.0;
            double thirdScore = chunks.get(2).getRelevanceScore() != null
                    ? chunks.get(2).getRelevanceScore() : 0.0;

            // If top 3 chunks all have good scores (0.35+), boost confidence by 5-10%
            if (secondScore >= 0.35 && thirdScore >= 0.35) {
                double consensusBoost = Math.min(0.10, (secondScore + thirdScore) / 10.0);
                confidence = Math.min(1.0, confidence + consensusBoost);
            }
        }

        // Cap at 99% - never show 100% confidence (stay humble)
        confidence = Math.min(0.99, confidence);

        log.debug("Confidence calculation: topScore={}, chunks={}, confidence={}",
                topScore, chunks.size(), confidence);

        return confidence;
    }

    private SearchResultDto toSearchResult(DocumentChunk chunk) {
        String documentName = documentRepository.findById(chunk.getDocumentId())
                .map(doc -> doc.getFileName())
                .orElse("Document");

        return SearchResultDto.builder()
                .chunkId(chunk.getId())
                .documentId(chunk.getDocumentId())
                .documentName(documentName)
                .content(chunk.getContent())
                .relevanceScore(chunk.getRelevanceScore())
                .chunkType(chunk.getChunkType())
                .score(chunk.getVectorScore())
                .build();
    }

    private SearchResponse buildEmptyResponse(String query, long startTime) {
        return SearchResponse.builder()
                .query(query)
                .answer("I couldn't find any relevant information in the documents.")
                .sources(List.of())
                .confidence(0.0)
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build();
    }
}