package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.adoption.entity.AdoptionStatus;

import java.time.LocalDateTime;

public record AdoptionRequestResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("catId") Long catId,
        @JsonProperty("adopterId") Long adopterId,
        @JsonProperty("organizationId") Long organizationId,
        @JsonProperty("status") AdoptionStatus status,
        @JsonProperty("notes") String notes,
        @JsonProperty("rejectionReason") String rejectionReason,
        @JsonProperty("createdAt") LocalDateTime createdAt,
        @JsonProperty("updatedAt") LocalDateTime updatedAt
) {}
