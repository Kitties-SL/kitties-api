package es.kitti.formanalysis.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NimMessage(
        @JsonProperty("role") String role,
        @JsonProperty("content") String content
) {}
