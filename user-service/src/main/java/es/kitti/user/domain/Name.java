package es.kitti.user.domain;

import es.kitti.mon.either.Validation;

public final class Name {

    private static final int MAX_LENGTH = 100;

    private final String value;

    private Name(String value) {
        this.value = value;
    }

    public static Validation<Name> of(String field, String raw) {
        if (raw == null || raw.isBlank())
            return Validation.invalidOne(field, "REQUIRED");
        if (raw.length() > MAX_LENGTH)
            return Validation.invalidOne(field, "INVALID_SIZE");
        return Validation.valid(new Name(raw.trim()));
    }

    public String value() { return value; }

    @Override
    public String toString() { return value; }
}
