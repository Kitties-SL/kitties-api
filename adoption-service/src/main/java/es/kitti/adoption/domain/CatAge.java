package es.kitti.adoption.domain;

import es.kitti.mon.either.Validation;

public final class CatAge {

    private static final int MIN = 0;
    private static final int MAX = 30;

    private final int value;

    private CatAge(int value) {
        this.value = value;
    }

    public static Validation<CatAge> of(Integer raw) {
        if (raw == null)
            return Validation.invalidOne("catAge", "REQUIRED");
        if (raw < MIN || raw > MAX)
            return Validation.invalidOne("catAge", "INVALID_RANGE");
        return Validation.valid(new CatAge(raw));
    }

    public int value() { return value; }

    @Override
    public String toString() { return String.valueOf(value); }
}
