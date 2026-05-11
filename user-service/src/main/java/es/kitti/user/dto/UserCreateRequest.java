package es.kitti.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import es.kitti.user.entity.UserRole;

import java.time.LocalDate;

public record UserCreateRequest(
        @JsonProperty("email")     String email,
        @JsonProperty("password")  String password,
        @JsonProperty("name")      @NotBlank String name,
        @JsonProperty("surname")   @NotBlank String surname,
        @JsonProperty("birthdate") LocalDate birthdate,
        @JsonProperty("status")    String status,
        @JsonProperty("role")      UserRole role
) {}