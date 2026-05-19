package es.kitti.formanalysis.dto.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StructuralAssessment(
        @JsonProperty("code") String code,
        @JsonProperty("severity") String severity,    // NONE | LOW | HIGH
        @JsonProperty("reasoning") String reasoning
) {}
