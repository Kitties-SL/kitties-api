package es.kitti.formanalysis.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record NimChatResponse(
        @JsonProperty("choices") List<NimChoice> choices
) {}
