package es.kitti.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.auth.domain.Email;

public record AuthRequest(
        @JsonProperty("email")    String email,
        @JsonProperty("password") String password
) {
    public Validation<AuthRequest> validate() {
        return Email.of(email)
                .zip(validatePassword(password), (e, p) -> new AuthRequest(e.value(), p));
    }

    private static Validation<String> validatePassword(String raw) {
        return raw == null || raw.isBlank()
                ? Validation.invalidOne("password", "REQUIRED")
                : Validation.valid(raw);
    }
}
