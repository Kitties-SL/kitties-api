package es.kitti.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;

public record BlockUserRequest(
        @JsonProperty("reason") String reason
) {
    public Validation<BlockUserRequest> validate() {
        var r = Validation.<BlockUserRequest>valid(this);
        if (reason != null && reason.length() > 500) r = r.and(Validation.invalidOne("reason", "INVALID_SIZE"));
        return r;
    }
}