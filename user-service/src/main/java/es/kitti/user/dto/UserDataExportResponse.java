package es.kitti.user.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record UserDataExportResponse(
        UserResponse profile,
        JsonNode adoptions,
        JsonNode chat
) {}
