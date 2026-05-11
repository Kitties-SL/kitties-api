package es.kitti.cat.domain;

import es.kitti.mon.either.Validation;

public final class City {

    private final String value;

    private City(String value) {
        this.value = value;
    }

    public static Validation<City> of(String raw) {
        if (raw == null || raw.isBlank())
            return Validation.invalidOne("city", "REQUIRED");
        return Validation.valid(new City(raw.trim()));
    }

    public String value() { return value; }

    @Override
    public String toString() { return value; }
}
