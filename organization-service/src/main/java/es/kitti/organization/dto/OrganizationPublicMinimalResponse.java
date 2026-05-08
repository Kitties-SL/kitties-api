package es.kitti.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrganizationPublicMinimalResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("city") String city,
        @JsonProperty("region") String region,
        @JsonProperty("phone") String phone,
        @JsonProperty("email") String email
) {}
