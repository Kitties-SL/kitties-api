package es.kitti.cat.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StorageResponse(
        @JsonProperty("key") String key,
        @JsonProperty("url") String url
) {}
