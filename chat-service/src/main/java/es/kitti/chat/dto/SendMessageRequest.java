package es.kitti.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @JsonProperty("content") @NotBlank @Size(max = 4000) String content
) {}
