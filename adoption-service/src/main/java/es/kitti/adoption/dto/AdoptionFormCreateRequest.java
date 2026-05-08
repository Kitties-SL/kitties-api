package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdoptionFormCreateRequest(
        @JsonProperty("fullName") @NotBlank String fullName,
        @JsonProperty("idNumber") @NotBlank String idNumber,
        @JsonProperty("phone") @NotBlank String phone,
        @JsonProperty("address") @NotBlank String address,
        @JsonProperty("city") @NotBlank String city,
        @JsonProperty("postalCode") @NotBlank String postalCode,
        @JsonProperty("acceptsVetVisits") @NotNull Boolean acceptsVetVisits,
        @JsonProperty("acceptsFollowUpContact") @NotNull Boolean acceptsFollowUpContact,
        @JsonProperty("acceptsReturnIfNeeded") @NotNull Boolean acceptsReturnIfNeeded,
        @JsonProperty("acceptsTermsAndConditions") @NotNull Boolean acceptsTermsAndConditions,
        @JsonProperty("consentHealthData") @NotNull Boolean consentHealthData,
        @JsonProperty("additionalNotes") String additionalNotes
) {}
