package es.kitti.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record PasswordResetTokenIssueResponse(
        @JsonProperty("token")     String token,
        @JsonProperty("jti")       String jti,
        @JsonProperty("expiresAt") LocalDateTime expiresAt
) {}
