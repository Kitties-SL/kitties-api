package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record InterviewResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("adoptionRequestId") Long adoptionRequestId,
        @JsonProperty("scheduledAt") LocalDateTime scheduledAt,
        @JsonProperty("notes") String notes,
        @JsonProperty("passed") Boolean passed,
        @JsonProperty("createdAt") LocalDateTime createdAt,
        @JsonProperty("updatedAt") LocalDateTime updatedAt
) {}
