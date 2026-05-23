package es.kitti.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.organization.entity.OrganizationPlan;

public record OrganizationPublicResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("region") String region,
        @JsonProperty("city") String city,
        @JsonProperty("logoUrl") String logoUrl,
        @JsonProperty("plan") OrganizationPlan plan,
        @JsonProperty("activeCatsCount") long activeCatsCount
) {}
