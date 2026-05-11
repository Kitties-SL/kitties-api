package es.kitti.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;

public record CreateConversationRequest(
        @JsonProperty("intakeRequestId") Long intakeRequestId,
        @JsonProperty("userId")          Long userId,
        @JsonProperty("organizationId")  Long organizationId
) {
    public Validation<CreateConversationRequest> validate() {
        var r = Validation.<CreateConversationRequest>valid(this);
        if (intakeRequestId == null) r = r.and(Validation.invalidOne("intakeRequestId", "REQUIRED"));
        if (userId == null)          r = r.and(Validation.invalidOne("userId",          "REQUIRED"));
        if (organizationId == null)  r = r.and(Validation.invalidOne("organizationId",  "REQUIRED"));
        return r;
    }
}
