package es.kitti.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PasswordResetTokenRequest(
        @JsonProperty("userId") Long userId
) {}
