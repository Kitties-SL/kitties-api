package es.kitti.user.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record PasswordResetRequestedEvent(
        @JsonProperty("userId")         Long userId,
        @JsonProperty("email")          String email,
        @JsonProperty("name")           String name,
        @JsonProperty("resetToken")     String resetToken,
        @JsonProperty("resetExpiresAt") LocalDateTime resetExpiresAt
) {}
