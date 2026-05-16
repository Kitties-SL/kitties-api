package es.kitti.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MembershipResponse(
        @JsonProperty("organizationId") Long organizationId,
        @JsonProperty("memberRole")     String memberRole
) {}
