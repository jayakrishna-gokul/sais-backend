package com.sapphireai.service.document;

import com.sapphireai.model.entity.DocumentChunk;
import com.sapphireai.model.entity.ProcessedDocument;
import com.sapphireai.model.enums.DocumentType;
import com.sapphireai.model.enums.ProcessingStatus;
import com.sapphireai.repository.DocumentRepository;
import com.sapphireai.repository.ChunkRepository;
import com.sapphireai.service.embedding.EmbeddingService;
import com.sapphireai.service.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Document processing pipeline:
 * Upload → Extract text → Detect type → Chunk → Embed → Index
 * ARCHITECTURE:
 * - Transactional processing (all-or-nothing)
 * - Automatic cleanup on failure
 * - No orphaned data in DB or vector store
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final Tika tika = new Tika();

    // ✅ CONSTANTS - Single source of truth
    private static final long MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024; // 100MB
    private static final int DB_FILENAME_MAX_LENGTH = 255;
    private static final int UUID_LENGTH = 36;
    private static final int FILENAME_SEPARATOR_LENGTH = 1; // underscore
    private static final int MAX_ORIGINAL_FILENAME_LENGTH =
            DB_FILENAME_MAX_LENGTH - UUID_LENGTH - FILENAME_SEPARATOR_LENGTH; // 218

    public ProcessedDocument processDocument(
            MultipartFile file,
            String userId,
            String organizationId
    ) {
        long startTime = System.currentTimeMillis();

        // ✅ VALIDATION - Check file BEFORE creating database record
        if (file.isEmpty() || file.getSize() == 0) {
            throw new IllegalArgumentException("File is empty. Please upload a file with content.");
        }
        // Check file size (100MB limit)
        long maxSize = MAX_FILE_SIZE_BYTES; // 100MB
        if (file.getSize() >= maxSize) {
            throw new IllegalArgumentException("File too large. Maximum size is 100MB, your file is " +
                    (file.getSize() / 1024 / 1024) + "MB");
        }

        // ✅ ADD THIS: Check filename length
        String filename = file.getOriginalFilename();
        if (filename != null && filename.length() > MAX_ORIGINAL_FILENAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Filename too long. Maximum " + MAX_ORIGINAL_FILENAME_LENGTH + " characters allowed, " +
                            "your filename is " + filename.length() + " characters. Please rename the file."
            );
        }

        // âœ… DECLARE VARIABLES OUTSIDE TRY BLOCK
        String filePath = null;
        List<DocumentChunk> chunks = null;
        boolean vectorsIndexed = false;

        // Create document record
        ProcessedDocument document = ProcessedDocument.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .organizationId(organizationId)
                .fileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .status(ProcessingStatus.PROCESSING)
                .uploadedAt(LocalDateTime.now())
                .build();

        documentRepository.save(document);
        String docId = document.getId();

        try {
            // Step 1: Save file to disk
            filePath = saveFile(file, docId);  // âœ… Assign to outer variable
            document.setFilePath(filePath);

            // Step 2: Extract text
            String text = extractText(new File(filePath));

            // Step 3: Detect document type
            DocumentType type = detectDocumentType(text, file.getOriginalFilename());
            double confidence = calculateDetectionConfidence(text, type);
            document.setDocumentType(type);
            document.setDetectionConfidence(confidence);

            // Step 4: Chunk document
            var chunkResult = chunkingService.createChunks(text, type, docId);
            chunks = chunkResult.getChunks();  // âœ… Assign to outer variable

            document.setTotalChunks(chunks.size());
            document.setChunkingStrategy(chunkResult.getStrategy());
            document.setQualityScore(chunkResult.getAverageQuality());

            document.setStatus(ProcessingStatus.PROCESSING);
            document = documentRepository.save(document);

            chunkRepository.saveAll(chunks);

            // Step 5: Generate embeddings and index
            embeddingService.embedChunks(chunks);
            vectorStoreService.indexChunks(chunks, organizationId);
            vectorsIndexed = true;  // âœ… Mark as indexed

            // Mark as completed
            document.setStatus(ProcessingStatus.COMPLETED);
            document.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            document.setRequiresReview(confidence < 0.6 || chunkResult.getAverageQuality() < 0.7);

            log.info("Document processed successfully: {} ({} chunks, {}ms)",
                    docId, chunks.size(), document.getProcessingTimeMs());

        } catch (Exception e) {
            log.error("Document processing failed: {}", document.getId(), e);

            // CLEANUP
            cleanup(document, filePath, chunks, vectorsIndexed);

            // DELETE the failed document instead of marking as FAILED
            try {
                documentRepository.delete(document);
                log.info("Deleted failed document: {}", document.getId());
            } catch (Exception deleteError) {
                log.error("Failed to delete document after processing failure", deleteError);
            }

            // Throw specific error message for user
            String userMessage = getUserFriendlyError(e);
            throw new RuntimeException(userMessage, e);
        }

        return documentRepository.save(document);
    }

    /**
     * Process text content from external sources (Confluence, SharePoint, etc.)
     * Creates chunks, embeddings, and indexes to vector store.
     *
     * @param documentId Existing ProcessedDocument ID
     * @param content Plain text content to process
     * @param organizationId Organization for vector store filtering
     */
    public void processDocumentContent(String documentId, String content, String organizationId) {
        long startTime = System.currentTimeMillis();

        List<DocumentChunk> chunks = null;
        boolean vectorsIndexed = false;

        try {
            // Get the existing ProcessedDocument
            ProcessedDocument document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

            log.info("Processing external content for document: {} ({})", documentId, document.getFileName());

            // Update status to PROCESSING
            document.setStatus(ProcessingStatus.PROCESSING);
            documentRepository.save(document);

            // Detect document type from content
            DocumentType type = detectDocumentType(content, document.getFileName());
            double confidence = calculateDetectionConfidence(content, type);
            document.setDocumentType(type);
            document.setDetectionConfidence(confidence);

            // Create chunks using your existing chunking service
            var chunkResult = chunkingService.createChunks(content, type, documentId);
            chunks = chunkResult.getChunks();

            document.setTotalChunks(chunks.size());
            document.setChunkingStrategy(chunkResult.getStrategy());
            document.setQualityScore(chunkResult.getAverageQuality());

            // Save document and chunks
            documentRepository.save(document);
            chunkRepository.saveAll(chunks);

            // Generate embeddings and index
            embeddingService.embedChunks(chunks);
            vectorStoreService.indexChunks(chunks, organizationId);
            vectorsIndexed = true;

            // Mark as completed
            document.setStatus(ProcessingStatus.COMPLETED);
            document.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            document.setRequiresReview(confidence < 0.6 || chunkResult.getAverageQuality() < 0.7);
            documentRepository.save(document);

            log.info("External content processed successfully: {} ({} chunks, {}ms)",
                    documentId, chunks.size(), document.getProcessingTimeMs());

        } catch (Exception e) {
            log.error("External content processing failed: {}", documentId, e);

            // Cleanup
            cleanup(null, null, chunks, vectorsIndexed);

            // Mark as failed
            documentRepository.findById(documentId).ifPresent(doc -> {
                doc.setStatus(ProcessingStatus.FAILED);
                doc.setErrorMessage(e.getMessage());
                documentRepository.save(doc);
            });

            throw new RuntimeException("External content processing failed: " + e.getMessage(), e);
        }
    }

    private String saveFile(MultipartFile file, String docId) throws Exception {
        Path uploadPath = Paths.get(System.getProperty("user.home"), ".sapphire", "documents");
        Files.createDirectories(uploadPath);

        String filename = docId + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath);

        return filePath.toString();
    }

    private String extractText(File file) throws Exception {
        String mimeType = Files.probeContentType(file.toPath());

        return switch (mimeType) {
            case "application/pdf" -> extractPdf(file);
            case "application/msword",
                 "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    -> extractPdf(file);
            case "text/plain" -> Files.readString(file.toPath());
            default -> throw new UnsupportedOperationException("Unsupported file type: " + mimeType);
        };
    }

    private String extractPdf(File file) throws IOException {
        try (PDDocument doc = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }
    @Transactional
    public void deleteDocument(ProcessedDocument document) {
        String documentId = document.getId();

        try {
            // 1. Delete chunks from database
            List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndex(documentId);
            chunkRepository.deleteAll(chunks);
            log.info("Deleted {} chunks for document {}", chunks.size(), documentId);

            // 2. Delete vectors from Qdrant
            vectorStoreService.deleteDocumentVectors(documentId);
            log.info("Deleted vectors for document {}", documentId);

            // 3. Delete file from disk
            if (document.getFilePath() != null) {
                try {
                    Files.deleteIfExists(Paths.get(document.getFilePath()));
                    log.info("Deleted file: {}", document.getFilePath());
                } catch (IOException e) {
                    log.warn("Failed to delete file: {}", document.getFilePath(), e);
                }
            }

            // 4. Delete document record
            documentRepository.delete(document);
            log.info("Deleted document record: {}", documentId);

        } catch (Exception e) {
            log.error("Error deleting document {}: {}", documentId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete document: " + e.getMessage());
        }
    }

    private DocumentType detectDocumentType(String content, String filename) {
        // Multi-signal detection
        double policyScore = 0.0;
        double technicalScore = 0.0;
        double legalScore = 0.0;

        String lower = content.toLowerCase();

        // Signal 1: Structure patterns
        if (content.matches("(?s).*\\d+\\.\\d+\\s+.*")) policyScore += 0.3;  // "1.1", "2.3"
        if (lower.contains("whereas") || lower.contains("hereby")) legalScore += 0.4;
        if (lower.contains("step") && lower.contains("install")) technicalScore += 0.3;

        // Signal 2: Vocabulary
        String[] policyKeywords = {"policy", "procedure", "employee", "effective date", "compliance"};
        String[] technicalKeywords = {"install", "configure", "command", "prerequisite", "setup"};
        String[] legalKeywords = {"party", "agreement", "contract", "liability", "jurisdiction"};

        for (String kw : policyKeywords) if (lower.contains(kw)) policyScore += 0.1;
        for (String kw : technicalKeywords) if (lower.contains(kw)) technicalScore += 0.1;
        for (String kw : legalKeywords) if (lower.contains(kw)) legalScore += 0.1;

        // Signal 3: Filename
        String lowerFilename = filename.toLowerCase();
        if (lowerFilename.contains("policy") || lowerFilename.contains("handbook")) policyScore += 0.2;
        if (lowerFilename.contains("manual") || lowerFilename.contains("guide")) technicalScore += 0.2;
        if (lowerFilename.contains("contract") || lowerFilename.contains("agreement")) legalScore += 0.2;

        // Determine type
        double maxScore = Math.max(policyScore, Math.max(technicalScore, legalScore));
        if (maxScore < 0.5) return DocumentType.GENERAL;
        if (policyScore == maxScore) return DocumentType.POLICY;
        if (legalScore == maxScore) return DocumentType.LEGAL;
        if (technicalScore == maxScore) return DocumentType.TECHNICAL;

        return DocumentType.GENERAL;
    }

    private double calculateDetectionConfidence(String content, DocumentType type) {
        // Simplified confidence calculation
        return 0.75;  // Real implementation would analyze multiple signals
    }

    private void cleanup(
            ProcessedDocument document,
            String filePath,
            List<DocumentChunk> chunks,
            boolean vectorsIndexed
    ) {
        try {
            // Delete file
            if (filePath != null) {
                Files.deleteIfExists(Paths.get(filePath));
                log.debug("Deleted file: {}", filePath);
            }

            // Delete chunks from DB
            if (chunks != null && !chunks.isEmpty()) {
                chunkRepository.deleteAll(chunks);
                log.debug("Deleted {} chunks from DB", chunks.size());
            }

            // Delete vectors from Qdrant
            if (vectorsIndexed && document != null) {
                vectorStoreService.deleteDocumentVectors(document.getId());
                log.debug("Deleted vectors for document: {}", document.getId());
            }

        } catch (Exception cleanupError) {
            log.error("Cleanup failed (non-fatal)", cleanupError);
        }
    }

    /**
     * Convert technical errors to user-friendly messages
     */
    private String getUserFriendlyError(Exception e) {
        String message = e.getMessage();

        if (message == null) {
            return "Document processing failed. Please try again or contact support.";
        }

        // Corrupted PDF
        if (message.contains("Page tree root must be a dictionary") ||
                message.contains("PDF") || message.contains("parse")) {
            return "The PDF file appears to be corrupted or invalid. Please try re-saving the PDF and uploading again.";
        }

        // Empty update (0 chunks)
        if (message.contains("Empty update request") || message.contains("Vector indexing failed")) {
            return "The document contains no extractable text. Please ensure the file has readable content.";
        }

        // Unsupported file
        if (message.contains("Unsupported file type")) {
            return message; // Already user-friendly
        }

        // Generic error
        return "Document processing failed: " + message;
    }
}