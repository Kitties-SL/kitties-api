package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdoptionPipelineStatsResponse(
        @JsonProperty("pending") long pending,
        @JsonProperty("reviewing") long reviewing,
        @JsonProperty("accepted") long accepted,
        @JsonProperty("formCompleted") long formCompleted,
        @JsonProperty("paymentPending") long paymentPending,
        @JsonProperty("paymentFailed") long paymentFailed,
        @JsonProperty("completed") long completed,
        @JsonProperty("rejected") long rejected
) {}
