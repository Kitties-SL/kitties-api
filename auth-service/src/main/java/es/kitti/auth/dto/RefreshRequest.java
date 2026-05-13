package es.kitti.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;

public record RefreshRequest(
        @JsonProperty("refreshToken") String refreshToken
) {
    public Validation<RefreshRequest> validate() {
        return Validation.valid(this)
                .requiredString("refreshToken", refreshToken));
    }
}
