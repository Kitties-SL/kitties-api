package es.kitti.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.chat.entity.SenderType;

import java.time.LocalDateTime;

public record MessageResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("conversationId") Long conversationId,
        @JsonProperty("senderId") Long senderId,
        @JsonProperty("senderType") SenderType senderType,
        @JsonProperty("content") String content,
        @JsonProperty("createdAt") LocalDateTime createdAt
) {}
