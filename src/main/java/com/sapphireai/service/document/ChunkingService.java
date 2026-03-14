package com.sapphireai.service.document;

import com.sapphireai.model.entity.DocumentChunk;
import com.sapphireai.model.enums.DocumentType;
import com.sapphireai.repository.ChunkRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Intelligent chunking service with parent-child architecture.
 * Strategy: Create small child chunks for search precision,
 * link to larger parent chunks for LLM context.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkingService {

    private final ChunkRepository chunkRepository;

    @Value("${sapphire.chunking.chunk-size:800}")
    private int chunkSize;

    @Value("${sapphire.chunking.chunk-overlap:200}")
    private int chunkOverlap;

    public ChunkResult createChunks(String content, DocumentType type, String documentId) {
        try {
            // Try type-specific chunking
            if (type == DocumentType.POLICY) {
                return policyChunking(content, documentId);
            } else if (type == DocumentType.TECHNICAL) {
                return technicalChunking(content, documentId);
            }
        } catch (Exception e) {
            log.warn("Type-specific chunking failed, using general strategy", e);
        }

        // Fallback: General semantic chunking
        return semanticChunking(content, documentId);
    }

    private ChunkResult semanticChunking(String content, String documentId) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int maxChunkSize = 2000; // Hard limit from config

        // Split by paragraphs
        String[] paragraphs = content.split("\n\n+");
        StringBuilder currentChunk = new StringBuilder();
        int chunkIndex = 0;

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;

            // If single paragraph exceeds max, split it first
            if (para.length() > maxChunkSize) {
                // Save current chunk if exists
                if (currentChunk.length() > 0) {
                    chunks.add(createChunk(currentChunk.toString(), documentId, chunkIndex++, "PARAGRAPH"));
                    currentChunk = new StringBuilder();
                }

                // Split large paragraph into smaller chunks
                chunks.addAll(splitLargeParagraph(para, documentId, chunkIndex, "PARAGRAPH"));
                chunkIndex += chunks.size();
                continue;
            }

            // If adding paragraph exceeds chunk size, save current chunk with overlap
            if (currentChunk.length() + para.length() > chunkSize && currentChunk.length() > 0) {
                chunks.add(createChunk(currentChunk.toString(), documentId, chunkIndex++, "PARAGRAPH"));

                // IMPROVED OVERLAP: Keep last 50 words for context continuity
                String chunkText = currentChunk.toString();
                String[] words = chunkText.split("\\s+");
                currentChunk = new StringBuilder();

                // Take last 50 words (or all if less than 50)
                int overlapWords = Math.min(50, words.length);
                int startPos = words.length - overlapWords;
                for (int i = startPos; i < words.length; i++) {
                    currentChunk.append(words[i]).append(" ");
                }
            }

            currentChunk.append(para).append("\n\n");
        }

        // Save final chunk
        if (currentChunk.length() > 0) {
            chunks.add(createChunk(currentChunk.toString(), documentId, chunkIndex, "PARAGRAPH"));
        }
        chunks.removeIf(chunk -> chunk.getContent().length() < 50);
        return new ChunkResult(chunks, "SemanticChunking", calculateAverageQuality(chunks));
    }

    /**
     * Split a large paragraph that exceeds max chunk size.
     * Splits at sentence boundaries to maintain readability.
     */
    private List<DocumentChunk> splitLargeParagraph(String paragraph, String documentId, int startIndex, String type) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int maxChunkSize = 2000;

        // Split into sentences
        String[] sentences = paragraph.split("(?<=[.!?])\\s+");
        StringBuilder currentChunk = new StringBuilder();
        int index = 0;

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;

            // If single sentence is too long, split it forcefully
            if (sentence.length() > maxChunkSize) {
                // Save current chunk if exists
                if (currentChunk.length() > 0) {
                    chunks.add(createChunk(currentChunk.toString().trim(), documentId, startIndex + index++, type));
                    currentChunk = new StringBuilder();
                }

                // Force-split long sentence at word boundaries
                String[] words = sentence.split("\\s+");
                StringBuilder forcedChunk = new StringBuilder();
                for (String word : words) {
                    if (forcedChunk.length() + word.length() + 1 > maxChunkSize) {
                        chunks.add(createChunk(forcedChunk.toString().trim(), documentId, startIndex + index++, type));
                        forcedChunk = new StringBuilder(word).append(" ");
                    } else {
                        forcedChunk.append(word).append(" ");
                    }
                }
                if (forcedChunk.length() > 0) {
                    chunks.add(createChunk(forcedChunk.toString().trim(), documentId, startIndex + index++, type));
                }
                continue;
            }

            // Check if adding sentence exceeds chunk size
            if (currentChunk.length() + sentence.length() + 1 > maxChunkSize) {
                if (currentChunk.length() > 0) {
                    chunks.add(createChunk(currentChunk.toString().trim(), documentId, startIndex + index++, type));
                }
                currentChunk = new StringBuilder(sentence).append(" ");
            } else {
                currentChunk.append(sentence).append(" ");
            }
        }

        // Save final chunk
        if (currentChunk.length() > 0) {
            chunks.add(createChunk(currentChunk.toString().trim(), documentId, startIndex + index, type));
        }

        return chunks;
    }

    private ChunkResult policyChunking(String content, String documentId) {
        List<DocumentChunk> chunks = new ArrayList<>();

        // Split by section numbers (1.1, 1.2, etc.)
        String[] sections = content.split("(?=\\d+\\.\\d+\\s)");
        int chunkIndex = 0;

        for (String section : sections) {
            section = section.trim();
            if (section.isEmpty()) continue;

            // If section too large, split further
            if (section.length() > chunkSize * 2) {
                chunks.addAll(splitLargeSection(section, documentId, chunkIndex, "POLICY_CLAUSE"));
                chunkIndex += chunks.size();
            } else {
                chunks.add(createChunk(section, documentId, chunkIndex++, "POLICY_CLAUSE"));
            }
        }
        chunks.removeIf(chunk -> chunk.getContent().length() < 50);
        //chunkRepository.saveAll(chunks);
        return new ChunkResult(chunks, "PolicyChunking", calculateAverageQuality(chunks));
    }

    private ChunkResult technicalChunking(String content, String documentId) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String[] steps = content.split("(?i)(?=Step \\d+|\\d+\\.)");
        int chunkIndex = 0;

        for (String step : steps) {
            step = step.trim();
            if (step.isEmpty()) continue;

            if (step.length() > chunkSize * 2) {
                // Step is too large — split it like a large section
                List<DocumentChunk> subChunks = splitLargeSection(step, documentId, chunkIndex, "PROCEDURE_STEP");
                chunks.addAll(subChunks);
                chunkIndex += subChunks.size();
            } else {
                chunks.add(createChunk(step, documentId, chunkIndex++, "PROCEDURE_STEP"));
            }
        }
        chunks.removeIf(chunk -> chunk.getContent().length() < 50);
        return new ChunkResult(chunks, "TechnicalChunking", calculateAverageQuality(chunks));
    }

    private List<DocumentChunk> splitLargeSection(String section, String documentId, int startIndex, String type) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int maxChunkSize = 2000;

        String[] sentences = section.split("(?<=[.!?])\\s+");
        StringBuilder currentChunk = new StringBuilder();
        int index = 0;

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;

            // If single sentence exceeds max, force split at word boundaries
            if (sentence.length() > maxChunkSize) {
                // Save current chunk first if exists
                if (currentChunk.length() > 0) {
                    chunks.add(createChunk(currentChunk.toString().trim(), documentId, startIndex + index++, type));
                    currentChunk = new StringBuilder();
                }
                // Force split long sentence at word boundaries
                String[] words = sentence.split("\\s+");
                StringBuilder forcedChunk = new StringBuilder();
                for (String word : words) {
                    if (forcedChunk.length() + word.length() + 1 > maxChunkSize) {
                        chunks.add(createChunk(forcedChunk.toString().trim(), documentId, startIndex + index++, type));
                        forcedChunk = new StringBuilder(word).append(" ");
                    } else {
                        forcedChunk.append(word).append(" ");
                    }
                }
                if (forcedChunk.length() > 0) {
                    chunks.add(createChunk(forcedChunk.toString().trim(), documentId, startIndex + index++, type));
                }
                continue; // ← skip the append below, already handled
            }

            // If adding sentence exceeds max, save current chunk
            if (currentChunk.length() + sentence.length() + 1 > maxChunkSize && currentChunk.length() > 0) {
                chunks.add(createChunk(currentChunk.toString().trim(), documentId, startIndex + index++, type));
                currentChunk = new StringBuilder();
            }

            currentChunk.append(sentence).append(" ");
        }

        if (currentChunk.length() > 0) {
            chunks.add(createChunk(currentChunk.toString().trim(), documentId, startIndex + index, type));
        }

        return chunks;
    }

    private DocumentChunk createChunk(String content, String documentId, int index, String type) {
        double quality = calculateChunkQuality(content);

        return DocumentChunk.builder()
                //.id(UUID.randomUUID().toString())
                .documentId(documentId)
                .chunkIndex(index)
                .content(content)
                .chunkType(type)
                .tokenCount(estimateTokens(content))
                .qualityScore(quality)
                .build();
    }

    private double calculateChunkQuality(String content) {
        double score = 1.0;

        // Penalize very short chunks
        if (content.length() < 100) score -= 0.3;

        // Penalize chunks ending mid-sentence
        if (!content.trim().endsWith(".") && !content.trim().endsWith("?")) score -= 0.2;

        // Penalize very long chunks
        if (content.length() > 2000) score -= 0.1;

        return Math.max(0.0, Math.min(1.0, score));
    }

    private double calculateAverageQuality(List<DocumentChunk> chunks) {
        return chunks.stream()
                .mapToDouble(DocumentChunk::getQualityScore)
                .average()
                .orElse(0.0);
    }

    private int estimateTokens(String text) {
        return text.length() / 4;  // Rough estimate: 1 token ≈ 4 chars
    }

    @Data
    public static class ChunkResult {
        private final List<DocumentChunk> chunks;
        private final String strategy;
        private final double averageQuality;
    }
}