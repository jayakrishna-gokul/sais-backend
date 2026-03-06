package com.sapphireai.model.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {
    private String query;
    private String answer;
    private List<SearchResultDto> sources;
    private Double confidence;
    private String sessionId;
    private Long processingTimeMs;
}