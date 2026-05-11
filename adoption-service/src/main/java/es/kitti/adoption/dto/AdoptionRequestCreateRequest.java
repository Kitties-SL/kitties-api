package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;

public record AdoptionRequestCreateRequest(
        @JsonProperty("catId")          Long catId,
        @JsonProperty("organizationId") Long organizationId
) {
    public Validation<AdoptionRequestCreateRequest> validate() {
        var result = Validation.<AdoptionRequestCreateRequest>valid(this);
        if (catId == null)          result = result.and(Validation.invalidOne("catId",          "REQUIRED"));
        if (organizationId == null) result = result.and(Validation.invalidOne("organizationId", "REQUIRED"));
        return result;
    }
}
