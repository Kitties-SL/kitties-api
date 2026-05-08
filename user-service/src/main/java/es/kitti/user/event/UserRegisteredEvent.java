package es.kitti.user.event;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserRegisteredEvent(
        @JsonProperty("userId") Long userId,
        @JsonProperty("email") String email,
        @JsonProperty("name") String name,
        @JsonProperty("activationToken") String activationToken
) {}
