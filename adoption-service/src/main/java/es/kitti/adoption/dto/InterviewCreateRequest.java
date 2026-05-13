package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.adoption.domain.FutureDateTime;

import java.time.LocalDateTime;

public record InterviewCreateRequest(
        @JsonProperty("scheduledAt") LocalDateTime scheduledAt,
        @JsonProperty("notes")       String notes
) {
    public Validation<InterviewCreateRequest> validate() {
        return FutureDateTime.of("scheduledAt", scheduledAt)
                .map(__ -> this);
    }
}
