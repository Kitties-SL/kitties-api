package es.kitti.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.user.entity.UserRole;

public record ChangeRoleRequest(
        @JsonProperty("role") UserRole role
) {
    public Validation<ChangeRoleRequest> validate() {
        return Validation.valid(this)
                .required("role", role);
    }
}
