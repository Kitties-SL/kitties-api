package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record AdoptionRequestCreateRequest(
        @JsonProperty("catId") @NotNull Long catId,
        @JsonProperty("organizationId") @NotNull Long organizationId
) {}
