package es.kitti.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UnreadCountResponse(
        @JsonProperty("count") long count
) {}
