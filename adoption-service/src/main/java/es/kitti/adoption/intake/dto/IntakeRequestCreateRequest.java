package es.kitti.adoption.intake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IntakeRequestCreateRequest(
        @JsonProperty("targetOrganizationId") @NotNull Long targetOrganizationId,
        @JsonProperty("catName") @NotBlank String catName,
        @JsonProperty("catAge") @NotNull @Min(0) @Max(30) Integer catAge,
        @JsonProperty("region") @NotBlank String region,
        @JsonProperty("city") @NotBlank String city,
        @JsonProperty("vaccinated") @NotNull Boolean vaccinated,
        @JsonProperty("description") String description
) {}
