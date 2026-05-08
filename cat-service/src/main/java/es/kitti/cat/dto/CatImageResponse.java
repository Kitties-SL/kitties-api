package es.kitti.cat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CatImageResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("url") String url,
        @JsonProperty("order") Integer order
) {}
