package es.kitti.formanalysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.formanalysis.entity.FlagSeverity;

public record FormFlagResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("severity") FlagSeverity severity,
        @JsonProperty("code") String code,
        @JsonProperty("description") String description
) {}
