package es.kitti.adoption.event;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdoptionRequestRejectedEvent(
        @JsonProperty("adoptionRequestId") Long adoptionRequestId,
        @JsonProperty("catId") Long catId,
        @JsonProperty("adopterId") Long adopterId,
        @JsonProperty("organizationId") Long organizationId,
        @JsonProperty("rejectionReason") String rejectionReason
) {}
