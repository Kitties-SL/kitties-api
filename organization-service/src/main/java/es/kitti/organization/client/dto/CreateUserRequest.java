package es.kitti.organization.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public record CreateUserRequest(
        @JsonProperty("email")     String email,
        @JsonProperty("password")  String password,
        @JsonProperty("name")      String name,
        @JsonProperty("surname")   String surname,
        @JsonProperty("birthdate") LocalDate birthdate
) {}
