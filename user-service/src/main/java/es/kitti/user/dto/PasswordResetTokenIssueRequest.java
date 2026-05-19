package es.kitti.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PasswordResetTokenIssueRequest(
        @JsonProperty("userId") Long userId
) {}
