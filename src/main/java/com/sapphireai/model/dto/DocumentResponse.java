package com.sapphireai.model.dto;

import com.sapphireai.model.enums.DocumentType;
import com.sapphireai.model.enums.ProcessingStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DocumentResponse {
    private String id;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private ProcessingStatus status;
    private DocumentType documentType;
    private Integer totalChunks;
    private Long processingTimeMs;
    private LocalDateTime uploadedAt;
}