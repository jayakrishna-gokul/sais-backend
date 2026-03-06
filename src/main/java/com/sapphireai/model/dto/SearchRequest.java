package com.sapphireai.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {

    @NotBlank(message = "Query is required")
    private String query;

    private String sessionId; // For multi-turn conversation

    private Boolean generateAnswer = true; // If false, return chunks only

    private Integer maxResults = 5; // Number of source chunks to return
}