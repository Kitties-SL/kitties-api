package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.adoption.entity.AdoptionStatus;
import es.kitti.mon.either.Validation;

public record AdoptionStatusUpdateRequest(
        @JsonProperty("status") AdoptionStatus status,
        @JsonProperty("reason") String reason
) {
    public Validation<AdoptionStatusUpdateRequest> validate() {
        return Validation.valid(this)
                .required("status", status);
    }
}
