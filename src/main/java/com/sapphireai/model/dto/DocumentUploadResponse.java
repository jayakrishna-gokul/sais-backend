package com.sapphireai.model.dto;

import com.sapphireai.model.enums.ProcessingStatus;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadResponse {
    private String documentId;
    private String fileName;
    private ProcessingStatus status;
    private Long fileSize;
    private LocalDateTime uploadedAt;
    private String estimatedProcessingTime;
}