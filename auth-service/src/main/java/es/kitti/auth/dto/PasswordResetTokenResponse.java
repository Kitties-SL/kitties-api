package es.kitti.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record PasswordResetTokenResponse(
        @JsonProperty("token")     String token,
        @JsonProperty("jti")       String jti,
        @JsonProperty("expiresAt") LocalDateTime expiresAt
) {}
