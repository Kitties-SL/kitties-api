package es.kitti.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;

public record PasswordPolicyUpdateRequest(
        @JsonProperty("strict") Boolean strict
) {
    public Validation<PasswordPolicyUpdateRequest> validate() {
        return Validation.valid(this).required("strict", strict);
    }
}
