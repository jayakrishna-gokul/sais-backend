package com.sapphireai.controller;

import com.sapphireai.model.dto.DocumentUploadResponse;
import com.sapphireai.model.dto.DocumentResponse;
import com.sapphireai.model.entity.audit.AuditEvent;
import com.sapphireai.model.enums.ProcessingStatus;
import com.sapphireai.config.Audited;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.sapphireai.model.entity.ProcessedDocument;
import com.sapphireai.model.entity.User;
import com.sapphireai.repository.DocumentRepository;
import com.sapphireai.repository.UserRepository;
import com.sapphireai.service.document.DocumentProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentProcessingService processingService;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @PostMapping("/upload")
    @Audited(eventType = AuditEvent.EventType.DOCUMENT, resourceType = "DOCUMENT")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        User user = getUser(authentication);
        try{
        ProcessedDocument doc = processingService.processDocument(
                file,
                user.getId(),
                user.getOrganizationId()
        );
        System.out.println("🔥 CONTROLLER - doc.getUploadedAt() = " + doc.getUploadedAt());
        DocumentUploadResponse response = DocumentUploadResponse.builder()
                .documentId(doc.getId())
                .fileName(doc.getFileName())
                .status(doc.getStatus())
                .fileSize(doc.getFileSize())
                .uploadedAt(doc.getUploadedAt())
                .estimatedProcessingTime("Processing in background")
                .build();

        return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            // Validation errors (empty file, too large, etc.)
            return ResponseEntity.badRequest()
                    .body(DocumentUploadResponse.builder()
                            .fileName(file.getOriginalFilename())
                            .status(ProcessingStatus.FAILED)
                            .estimatedProcessingTime(e.getMessage())  // Error message
                            .build());

        } catch (RuntimeException e) {
            // Processing errors (corrupted PDF, etc.)
            return ResponseEntity.status(422)  // Unprocessable Entity
                    .body(DocumentUploadResponse.builder()
                            .fileName(file.getOriginalFilename())
                            .status(ProcessingStatus.FAILED)
                            .estimatedProcessingTime(e.getMessage())  // Error message
                            .build());
        }
    }

    @PostMapping("/documents/upload-chunked")
    public ResponseEntity<String> uploadLarge(
            @RequestParam("file") MultipartFile file,
            @RequestParam("chunkIndex") int index,
            @RequestParam("totalChunks") int total,
            @RequestParam("uploadId") String uploadId
    ) {
        // Combine chunks when complete
        // Supports GB-sized files
        return ResponseEntity.ok("");
    }

    @GetMapping
    public ResponseEntity<Page<DocumentResponse>> listDocuments(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ProcessingStatus status,
            Pageable pageable
    ) {
        User user = getUser(authentication);

        Page<ProcessedDocument> documents;
        if (status != null) {
            documents = documentRepository.findByOrganizationIdAndStatus(
                    user.getOrganizationId(),
                    status,
                    PageRequest.of(page, size, Sort.by("uploadedAt").descending())
            );
        } else {
            documents = documentRepository.findByOrganizationId(
                    user.getOrganizationId(),
                    PageRequest.of(page, size, Sort.by("uploadedAt").descending())
            );
        }

        Page<DocumentResponse> response = documents.map(doc -> DocumentResponse.builder()
                .id(doc.getId())
                .fileName(doc.getFileName())
                .contentType(doc.getContentType())
                .fileSize(doc.getFileSize())
                .status(doc.getStatus())
                .documentType(doc.getDocumentType())
                .totalChunks(doc.getTotalChunks())
                .processingTimeMs(doc.getProcessingTimeMs())
                .uploadedAt(doc.getUploadedAt())
                .build());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProcessedDocument> getDocument(
            @PathVariable String id,
            Authentication authentication
    ) {
        User user = getUser(authentication);
        ProcessedDocument doc = documentRepository.findById(id)
                .filter(d -> d.getOrganizationId().equals(user.getOrganizationId()))
                .orElseThrow(() -> new RuntimeException("Document not found"));

        return ResponseEntity.ok(doc);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable String id,
            Authentication authentication
    ) {
        User user = getUser(authentication);

        // Security check: user can only delete their own uploads
        ProcessedDocument doc = documentRepository.findByIdAndOrganizationIdAndUserId(
                id,
                user.getOrganizationId(),
                user.getId()
        ).orElseThrow(() -> new RuntimeException("Document not found or access denied"));

        // Delete cascade
        processingService.deleteDocument(doc);

        return ResponseEntity.noContent().build();
    }

    private User getUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}