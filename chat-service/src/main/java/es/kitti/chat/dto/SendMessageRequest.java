package es.kitti.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;

public record SendMessageRequest(
        @JsonProperty("content") String content
) {
    public Validation<SendMessageRequest> validate() {
        var r = Validation.<SendMessageRequest>valid(this);
        if (content == null || content.isBlank())
            r = r.and(Validation.invalidOne("content", "REQUIRED"));
        else if (content.length() > 4000)
            r = r.and(Validation.invalidOne("content", "INVALID_SIZE"));
        return r;
    }
}
