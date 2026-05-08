package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record InterviewCreateRequest(
        @JsonProperty("scheduledAt") @NotNull @Future LocalDateTime scheduledAt,
        @JsonProperty("notes") String notes
) {}
