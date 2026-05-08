package es.kitti.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import es.kitti.organization.entity.MemberRole;

public record ChangeMemberRoleRequest(
        @JsonProperty("role") @NotNull MemberRole role
) {}
