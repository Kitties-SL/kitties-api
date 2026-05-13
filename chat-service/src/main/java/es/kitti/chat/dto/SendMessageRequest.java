package es.kitti.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.chat.domain.MessageContent;
import es.kitti.mon.either.Validation;

public record SendMessageRequest(
        @JsonProperty("content") String content
) {
    public Validation<SendMessageRequest> validate() {
        return Validation.valid(this)
                .and(MessageContent.of(content));
    }
}
