package es.kitti.adoption.intake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.adoption.domain.Name;
import es.kitti.mon.either.Validation;

public record IntakeDecisionRequest(
        @JsonProperty("reason") String reason
) {
    public Validation<IntakeDecisionRequest> validate() {
        return Name.of("reason", reason).map(__ -> this);
    }
}
