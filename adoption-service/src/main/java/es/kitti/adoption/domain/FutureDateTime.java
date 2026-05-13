package es.kitti.adoption.domain;

import es.kitti.mon.either.Validation;

import java.time.LocalDateTime;

public final class FutureDateTime {

    private final LocalDateTime value;

    private FutureDateTime(LocalDateTime value) {
        this.value = value;
    }

    public static Validation<FutureDateTime> of(String field, LocalDateTime raw) {
        if (raw == null)
            return Validation.invalidOne(field, "REQUIRED");
        if (!raw.isAfter(LocalDateTime.now()))
            return Validation.invalidOne(field, "MUST_BE_FUTURE");
        return Validation.valid(new FutureDateTime(raw));
    }

    public LocalDateTime value() { return value; }

    @Override
    public String toString() { return value.toString(); }
}
