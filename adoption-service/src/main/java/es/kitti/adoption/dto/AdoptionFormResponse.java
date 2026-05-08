package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record AdoptionFormResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("adoptionRequestId") Long adoptionRequestId,
        @JsonProperty("fullName") String fullName,
        @JsonProperty("idNumber") String idNumber,
        @JsonProperty("phone") String phone,
        @JsonProperty("address") String address,
        @JsonProperty("city") String city,
        @JsonProperty("postalCode") String postalCode,
        @JsonProperty("acceptsVetVisits") Boolean acceptsVetVisits,
        @JsonProperty("acceptsFollowUpContact") Boolean acceptsFollowUpContact,
        @JsonProperty("acceptsReturnIfNeeded") Boolean acceptsReturnIfNeeded,
        @JsonProperty("acceptsTermsAndConditions") Boolean acceptsTermsAndConditions,
        @JsonProperty("consentHealthData") Boolean consentHealthData,
        @JsonProperty("additionalNotes") String additionalNotes,
        @JsonProperty("signedAdoptionContract") Boolean signedAdoptionContract,
        @JsonProperty("contractSignedAt") LocalDateTime contractSignedAt,
        @JsonProperty("createdAt") LocalDateTime createdAt,
        @JsonProperty("updatedAt") LocalDateTime updatedAt
) {}
