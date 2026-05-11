package es.kitti.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.organization.entity.MemberRole;

public record ChangeMemberRoleRequest(
        @JsonProperty("role") MemberRole role
) {
    public Validation<ChangeMemberRoleRequest> validate() {
        var r = Validation.<ChangeMemberRoleRequest>valid(this);
        if (role == null) r = r.and(Validation.invalidOne("role", "REQUIRED"));
        return r;
    }
}
