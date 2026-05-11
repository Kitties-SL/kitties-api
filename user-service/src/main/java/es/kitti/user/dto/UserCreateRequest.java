package es.kitti.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.user.domain.Email;
import es.kitti.user.domain.Password;
import es.kitti.user.entity.UserRole;

import java.time.LocalDate;

public record UserCreateRequest(
        @JsonProperty("email")     String email,
        @JsonProperty("password")  String password,
        @JsonProperty("name")      String name,
        @JsonProperty("surname")   String surname,
        @JsonProperty("birthdate") LocalDate birthdate,
        @JsonProperty("status")    String status,
        @JsonProperty("role")      UserRole role
) {
    public Validation<UserCreateRequest> validate() {
        return Email.of(email)
                .zip(Password.of(password), (e, p) ->
                        new UserCreateRequest(e.value(), p.value(), name, surname, birthdate, status, role)
                )
                .and(name == null || name.isBlank()
                        ? Validation.invalidOne("name", "REQUIRED")
                        : Validation.valid(name))
                .and(surname == null || surname.isBlank()
                        ? Validation.invalidOne("surname", "REQUIRED")
                        : Validation.valid(surname));
    }
}
