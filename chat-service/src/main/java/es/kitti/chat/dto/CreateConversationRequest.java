package es.kitti.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;

public record CreateConversationRequest(
        @JsonProperty("intakeRequestId") Long intakeRequestId,
        @JsonProperty("userId")          Long userId,
        @JsonProperty("organizationId")  Long organizationId
) {
    public Validation<CreateConversationRequest> validate() {
        return Validation.valid(this)
                .and(Validation.required("intakeRequestId", intakeRequestId))
                .and(Validation.required("userId",          userId))
                .and(Validation.required("organizationId",  organizationId));
    }
}
