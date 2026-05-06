package es.kitti.chat.dto;

import java.util.List;

public record ChatDataExport(List<ConversationExportEntry> conversations) {}
