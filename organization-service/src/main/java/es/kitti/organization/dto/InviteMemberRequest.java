package es.kitti.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.organization.entity.MemberRole;

public record InviteMemberRequest(
        @JsonProperty("userId") Long userId,
        @JsonProperty("role")   MemberRole role
) {
    public Validation<InviteMemberRequest> validate() {
        var r = Validation.<InviteMemberRequest>valid(this);
        if (userId == null) r = r.and(Validation.invalidOne("userId", "REQUIRED"));
        if (role == null)   r = r.and(Validation.invalidOne("role",   "REQUIRED"));
        return r;
    }
}
