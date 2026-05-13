package es.kitti.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.organization.entity.MemberRole;

public record ChangeMemberRoleRequest(
        @JsonProperty("role") MemberRole role
) {
    public Validation<ChangeMemberRoleRequest> validate() {
        return Validation.valid(this)
                .required("role", role));
    }
}
