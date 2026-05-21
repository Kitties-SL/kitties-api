package es.kitti.adoption.intake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.adoption.client.dto.CatResponse;

public record IntakeApprovedResponse(
        @JsonProperty("intakeRequest") IntakeRequestResponse intakeRequest,
        @JsonProperty("createdCat")    CatResponse createdCat
) {}
