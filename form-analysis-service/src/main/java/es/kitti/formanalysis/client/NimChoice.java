package es.kitti.formanalysis.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NimChoice(
        @JsonProperty("message") NimMessage message
) {}
