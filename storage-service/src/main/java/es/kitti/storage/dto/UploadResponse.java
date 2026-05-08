package es.kitti.storage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UploadResponse(
        @JsonProperty("key") String key,
        @JsonProperty("url") String url
) {}
