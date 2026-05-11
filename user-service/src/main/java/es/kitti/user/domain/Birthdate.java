package es.kitti.user.domain;

import es.kitti.mon.either.Validation;

import java.time.LocalDate;

public final class Birthdate {

    private final LocalDate value;

    private Birthdate(LocalDate value) {
        this.value = value;
    }

    public static Validation<Birthdate> of(LocalDate raw) {
        if (raw == null)
            return Validation.invalidOne("birthdate", "REQUIRED");
        if (!raw.isBefore(LocalDate.now()))
            return Validation.invalidOne("birthdate", "INVALID_DATE");
        return Validation.valid(new Birthdate(raw));
    }

    public LocalDate value() { return value; }

    @Override
    public String toString() { return value.toString(); }
}
