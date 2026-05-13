package es.kitti.adoption.domain;

import es.kitti.mon.either.Validation;

public final class Phone {

    private final String value;

    private Phone(String value) {
        this.value = value;
    }

    public static Validation<Phone> of(String raw) {
        if (raw == null || raw.isBlank())
            return Validation.invalidOne("phone", "REQUIRED");
        return Validation.valid(new Phone(raw.trim()));
    }

    public String value() { return value; }

    @Override
    public String toString() { return value; }
}
