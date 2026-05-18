package es.kitti.formanalysis.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NimResponseFormat(
        @JsonProperty("type") String type
) {}
