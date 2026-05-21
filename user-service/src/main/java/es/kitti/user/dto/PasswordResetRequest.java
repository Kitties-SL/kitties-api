package es.kitti.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;

public record PasswordResetRequest(
        @JsonProperty("token")       String token,
        @JsonProperty("newPassword") String newPassword
) {
    private static final int MIN_LENGTH = 8;

    public Validation<PasswordResetRequest> validate() {
        return Validation.valid(this)
                .requiredString("token", token)
                .and(newPassword(newPassword));
    }

    private static Validation<?> newPassword(String value) {
        if (value == null || value.isBlank()) return Validation.invalidOne("newPassword", "REQUIRED");
        if (value.length() < MIN_LENGTH)      return Validation.invalidOne("newPassword", "INVALID_SIZE");
        return Validation.valid(value);
    }
}
