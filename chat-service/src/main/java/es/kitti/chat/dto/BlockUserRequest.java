package es.kitti.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public record BlockUserRequest(
        @JsonProperty("reason") @Size(max = 500) String reason
) {}
