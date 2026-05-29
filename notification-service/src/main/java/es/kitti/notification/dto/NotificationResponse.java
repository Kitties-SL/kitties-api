package es.kitti.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record NotificationResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("type") String type,
        @JsonProperty("code") String code,
        @JsonProperty("title") String title,
        @JsonProperty("body") String body,
        @JsonProperty("metadata") String metadata,
        @JsonProperty("read") boolean read,
        @JsonProperty("readAt") LocalDateTime readAt,
        @JsonProperty("createdAt") LocalDateTime createdAt
) {}
