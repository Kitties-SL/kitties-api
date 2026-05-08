package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import es.kitti.adoption.entity.AdoptionStatus;

public record AdoptionStatusUpdateRequest(
        @JsonProperty("status") @NotNull AdoptionStatus status,
        @JsonProperty("reason") String reason
) {}
