package es.kitti.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record CreateConversationRequest(
        @JsonProperty("intakeRequestId") @NotNull Long intakeRequestId,
        @JsonProperty("userId") @NotNull Long userId,
        @JsonProperty("organizationId") @NotNull Long organizationId
) {}
