package es.kitti.cat.domain;

import es.kitti.mon.either.Validation;

public final class Country {

    private final String value;

    private Country(String value) {
        this.value = value;
    }

    public static Validation<Country> of(String raw) {
        if (raw == null || raw.isBlank())
            return Validation.invalidOne("country", "REQUIRED");
        return Validation.valid(new Country(raw.trim()));
    }

    public String value() { return value; }

    @Override
    public String toString() { return value; }
}
