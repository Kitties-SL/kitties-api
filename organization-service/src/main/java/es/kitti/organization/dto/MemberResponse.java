package es.kitti.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.organization.entity.MemberRole;
import es.kitti.organization.entity.MemberStatus;

import java.time.LocalDateTime;

public record MemberResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("organizationId") Long organizationId,
        @JsonProperty("userId") Long userId,
        @JsonProperty("role") MemberRole role,
        @JsonProperty("status") MemberStatus status,
        @JsonProperty("joinedAt") LocalDateTime joinedAt
) {}
