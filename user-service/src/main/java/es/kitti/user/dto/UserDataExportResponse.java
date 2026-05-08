package es.kitti.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record UserDataExportResponse(
        @JsonProperty("profile") UserResponse profile,
        @JsonProperty("adoptions") JsonNode adoptions,
        @JsonProperty("chat") JsonNode chat
) {}
