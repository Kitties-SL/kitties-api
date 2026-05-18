package es.kitti.formanalysis.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record NimChatRequest(
        @JsonProperty("model") String model,
        @JsonProperty("messages") List<NimMessage> messages,
        @JsonProperty("response_format") NimResponseFormat responseFormat
) {}
