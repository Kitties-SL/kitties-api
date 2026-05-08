package es.kitti.adoption.intake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record IntakeDecisionRequest(
        @JsonProperty("reason") @NotBlank String reason
) {}
