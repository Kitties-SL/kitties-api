package es.kitti.adoption.intake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IntakePipelineStatsResponse(
        @JsonProperty("pending") long pending,
        @JsonProperty("approved") long approved,
        @JsonProperty("rejected") long rejected
) {}
