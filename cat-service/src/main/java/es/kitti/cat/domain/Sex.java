package es.kitti.cat.domain;

import es.kitti.cat.entity.CatSex;
import es.kitti.mon.either.Validation;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class Sex {

    private static final Set<String> VALID_VALUES = Arrays.stream(CatSex.values())
            .map(Enum::name)
            .collect(Collectors.toSet());

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