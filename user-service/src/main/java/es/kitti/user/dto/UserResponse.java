package es.kitti.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.user.entity.UserRole;
import es.kitti.user.entity.UserStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("email") String email,
        @JsonProperty("name") String name,
        @JsonProperty("surname") String surname,
        @JsonProperty("status") UserStatus status,
        @JsonProperty("role") UserRole role,
        @JsonProperty("birthdate") LocalDate birthdate,
        @JsonProperty("createdAt") LocalDateTime createdAt,
        @JsonProperty("updatedAt") LocalDateTime updatedAt
) {}
