package es.kitti.chat.dto;

import java.util.List;

public record ConversationExportEntry(
        ConversationResponse conversation,
        List<MessageResponse> messages
) {}
