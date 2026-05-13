package es.kitti.adoption.domain;

import es.kitti.mon.either.Validation;

public final class Address {

    private final String value;

    private Address(String value) {
        this.value = value;
    }

    public static Validation<Address> of(String raw) {
        if (raw == null || raw.isBlank())
            return Validation.invalidOne("address", "REQUIRED");
        return Validation.valid(new Address(raw.trim()));
    }

    public String value() { return value; }

    @Override
    public String toString() { return value; }
}
