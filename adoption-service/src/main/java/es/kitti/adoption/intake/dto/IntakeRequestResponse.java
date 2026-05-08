package es.kitti.adoption.intake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.adoption.intake.entity.IntakeStatus;

import java.time.LocalDateTime;

public record IntakeRequestResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("userId") Long userId,
        @JsonProperty("targetOrganizationId") Long targetOrganizationId,
        @JsonProperty("catName") String catName,
        @JsonProperty("catAge") Integer catAge,
        @JsonProperty("region") String region,
        @JsonProperty("city") String city,
        @JsonProperty("vaccinated") Boolean vaccinated,
        @JsonProperty("description") String description,
        @JsonProperty("status") IntakeStatus status,
        @JsonProperty("rejectionReason") String rejectionReason,
        @JsonProperty("createdAt") LocalDateTime createdAt,
        @JsonProperty("decidedAt") LocalDateTime decidedAt
) {}
