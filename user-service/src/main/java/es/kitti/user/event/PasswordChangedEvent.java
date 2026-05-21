package es.kitti.user.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record PasswordChangedEvent(
        @JsonProperty("userId")          Long userId,
        @JsonProperty("email")           String email,
        @JsonProperty("name")            String name,
        @JsonProperty("requestIp")       String requestIp,
        @JsonProperty("changedAt")       LocalDateTime changedAt,
        @JsonProperty("resetToken")      String resetToken,
        @JsonProperty("resetExpiresAt")  LocalDateTime resetExpiresAt
) {}
