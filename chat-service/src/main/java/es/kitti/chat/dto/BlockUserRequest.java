package es.kitti.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;

public record BlockUserRequest(
        @JsonProperty("reason") String reason
) {
    public Validation<BlockUserRequest> validate() {
        return Validation.valid(this)
                .optional(reason, r -> r.length() > 500
                        ? Validation.invalidOne("reason", "INVALID_SIZE")
                        : Validation.valid(r));
    }
}