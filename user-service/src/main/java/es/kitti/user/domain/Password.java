package es.kitti.user.domain;

import es.kitti.mon.either.Validation;

public final class Password {

    private static final int MIN_LENGTH = 8;

    private final String value;

    private Password(String value) {
        this.value = value;
    }

    public static Validation<Password> of(String raw) {
        if (raw == null || raw.isBlank())
            return Validation.invalidOne("password", "REQUIRED");
        if (raw.length() < MIN_LENGTH)
            return Validation.invalidOne("password", "INVALID_SIZE");
        return Validation.valid(new Password(raw));
    }

    public String value() { return value; }
}