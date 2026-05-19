package es.kitti.formanalysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.formanalysis.entity.AnalysisDecision;

import java.time.LocalDateTime;
import java.util.List;

public record FormAnalysisDetailResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("adoptionRequestId") Long adoptionRequestId,
        @JsonProperty("organizationId") Long organizationId,
        @JsonProperty("decision") AnalysisDecision decision,
        @JsonProperty("rejectionReason") String rejectionReason,
        @JsonProperty("criticalFlags") int criticalFlags,
        @JsonProperty("warningFlags") int warningFlags,
        @JsonProperty("noticeFlags") int noticeFlags,
        @JsonProperty("llmReasoning") String llmReasoning,
        @JsonProperty("createdAt") LocalDateTime createdAt,
        @JsonProperty("flags") List<FormFlagResponse> flags
) {}
