package es.kitti.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateOrganizationRequest(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("address") String address,
        @JsonProperty("city") String city,
        @JsonProperty("region") String region,
        @JsonProperty("country") String country,
        @JsonProperty("phone") String phone,
        @JsonProperty("email") String email,
        @JsonProperty("logoUrl") String logoUrl
) {}
