package es.kitti.adoption.domain;

import es.kitti.mon.either.Validation;

import java.util.Set;

public final class Sex {

    private static final Set<String> VALID_VALUES = Set.of("Male", "Female");

    private final String value;

    private Sex(String value) {
        this.value = value;
    }

    public static Validation<Sex> of(String raw) {
        if (raw == null || raw.isBlank())
            return Validation.invalidOne("sex", "REQUIRED");
        if (!VALID_VALUES.contains(raw))
            return Validation.invalidOne("sex", "INVALID_VALUE");
        return Validation.valid(new Sex(raw));
    }

    public String value() { return value; }
}
