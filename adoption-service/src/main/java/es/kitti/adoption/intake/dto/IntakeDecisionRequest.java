package es.kitti.adoption.intake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.adoption.domain.Name;

public record IntakeDecisionRequest(
        @JsonProperty("reason") String reason
) {
    public Validation<IntakeDecisionRequest> validate() {
        return Name.of("reason", reason).map(__ -> this);
    }
}
