package es.kitti.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public record UserUpdateRequest(
        @JsonProperty("name") String name,
        @JsonProperty("surname") String surname,
        @JsonProperty("birthdate") LocalDate birthdate
) {}
