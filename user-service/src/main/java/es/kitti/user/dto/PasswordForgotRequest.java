package es.kitti.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;

public record PasswordForgotRequest(
        @JsonProperty("email") String email
) {
    public Validation<PasswordForgotRequest> validate() {
        return Validation.valid(this)
                .requiredString("email", email);
    }
}
