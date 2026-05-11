package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.adoption.entity.AdoptionStatus;

public record AdoptionStatusUpdateRequest(
        @JsonProperty("status") AdoptionStatus status,
        @JsonProperty("reason") String reason
) {
    public Validation<AdoptionStatusUpdateRequest> validate() {
        if (status == null)
            return Validation.invalidOne("status", "REQUIRED");
        return Validation.valid(this);
    }
}
