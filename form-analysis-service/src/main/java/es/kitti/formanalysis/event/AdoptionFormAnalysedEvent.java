package es.kitti.formanalysis.event;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdoptionFormAnalysedEvent(
        @JsonProperty("adoptionRequestId") Long adoptionRequestId,
        @JsonProperty("decision") String decision,
        @JsonProperty("rejectionReason") String rejectionReason,
        @JsonProperty("adopterId") Long adopterId,
        @JsonProperty("criticalFlags") int criticalFlags,
        @JsonProperty("warningFlags") int warningFlags,
        @JsonProperty("noticeFlags") int noticeFlags
) {}
