package es.kitti.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;

public record PasswordRollbackRequest(
        @JsonProperty("token") String token
) {
    public Validation<PasswordRollbackRequest> validate() {
        return Validation.valid(this).requiredString("token", token);
    }
}
