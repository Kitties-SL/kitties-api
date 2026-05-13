package es.kitti.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.organization.entity.MemberRole;

public record InviteMemberRequest(
        @JsonProperty("userId") Long userId,
        @JsonProperty("role")   MemberRole role
) {
    public Validation<InviteMemberRequest> validate() {
        return Validation.valid(this)
                .and(Validation.required("userId", userId))
                .and(Validation.required("role",   role));
    }
}
