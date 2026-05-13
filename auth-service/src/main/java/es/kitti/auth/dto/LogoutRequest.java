package es.kitti.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;

public record LogoutRequest(
        @JsonProperty("refreshToken") String refreshToken
) {
    public Validation<LogoutRequest> validate() {
        return Validation.valid(this)
                .and(Validation.requiredString("refreshToken", refreshToken));
    }
}
