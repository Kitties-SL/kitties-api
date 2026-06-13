package es.kitti.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NearbyOrganizationResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("city") String city,
        @JsonProperty("region") String region,
        @JsonProperty("logoUrl") String logoUrl,
        @JsonProperty("distanceKm") double distanceKm
) {}