package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;

public record AdoptionRequestCreateRequest(
        @JsonProperty("catId")          Long catId,
        @JsonProperty("organizationId") Long organizationId
) {
    public Validation<AdoptionRequestCreateRequest> validate() {
        return Validation.valid(this)
                .and(Validation.required("catId",          catId))
                .and(Validation.required("organizationId", organizationId));
    }
}
