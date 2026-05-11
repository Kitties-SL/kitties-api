package es.kitti.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.user.domain.Name;

import java.time.LocalDate;

public record UserUpdateRequest(
        @JsonProperty("name")      String name,
        @JsonProperty("surname")   String surname,
        @JsonProperty("birthdate") LocalDate birthdate
) {
    public Validation<UserUpdateRequest> validate() {
        var result = Validation.<UserUpdateRequest>valid(this);
        if (name != null)    result = result.and(Name.of("name", name));
        if (surname != null) result = result.and(Name.of("surname", surname));
        return result;
    }
}
