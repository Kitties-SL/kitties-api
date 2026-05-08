package es.kitti.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record ConversationResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("intakeRequestId") Long intakeRequestId,
        @JsonProperty("userId") Long userId,
        @JsonProperty("organizationId") Long organizationId,
        @JsonProperty("createdAt") LocalDateTime createdAt,
        @JsonProperty("lastMessageAt") LocalDateTime lastMessageAt
) {}
