package es.kitti.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ConversationExportEntry(
        @JsonProperty("conversation") ConversationResponse conversation,
        @JsonProperty("messages") List<MessageResponse> messages
) {}
