package es.kitti.adoption.intake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.adoption.intake.client.OrganizationPublicMinimal;

import java.util.List;

public record IntakeRejectionResponse(
        @JsonProperty("intake") IntakeRequestResponse intake,
        @JsonProperty("alternatives") List<OrganizationPublicMinimal> alternatives
) {}
