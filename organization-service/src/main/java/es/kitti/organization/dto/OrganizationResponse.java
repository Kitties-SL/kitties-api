package es.kitti.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.organization.entity.OrganizationPlan;
import es.kitti.organization.entity.OrganizationStatus;

import java.time.LocalDateTime;

public record OrganizationResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("address") String address,
        @JsonProperty("city") String city,
        @JsonProperty("region") String region,
        @JsonProperty("country") String country,
        @JsonProperty("phone") String phone,
        @JsonProperty("email") String email,
        @JsonProperty("logoUrl") String logoUrl,
        @JsonProperty("status") OrganizationStatus status,
        @JsonProperty("plan") OrganizationPlan plan,
        @JsonProperty("maxMembers") int maxMembers,
        @JsonProperty("createdAt") LocalDateTime createdAt,
        @JsonProperty("updatedAt") LocalDateTime updatedAt
) {}
