package es.kitti.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthResponse(
        @JsonProperty("accessToken")  String accessToken,
        @JsonProperty("refreshToken") String refreshToken,
        @JsonProperty("expiresIn")    long expiresIn
) { }
