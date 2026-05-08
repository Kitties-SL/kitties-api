package es.kitti.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ChatDataExport(@JsonProperty("conversations") List<ConversationExportEntry> conversations) {}
