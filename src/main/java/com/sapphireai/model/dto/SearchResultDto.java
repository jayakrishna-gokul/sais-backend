package com.sapphireai.model.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultDto {
    private String chunkId;
    private String documentId;
    private String documentName;
    private String content;
    private Double relevanceScore;
    private String chunkType;
    private String section;
    private Integer page;
    private double score;
}