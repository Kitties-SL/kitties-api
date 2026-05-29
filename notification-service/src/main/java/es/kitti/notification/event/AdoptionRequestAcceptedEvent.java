package es.kitti.notification.event;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdoptionRequestAcceptedEvent(
        @JsonProperty("adoptionRequestId") Long adoptionRequestId,
        @JsonProperty("catId") Long catId,
        @JsonProperty("adopterId") Long adopterId,
        @JsonProperty("organizationId") Long organizationId
) {}