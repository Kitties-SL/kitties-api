package es.kitti.adoption.domain;

import es.kitti.mon.either.Validation;

public final class PostalCode {

    private final String value;

    private PostalCode(String value) {
        this.value = value;
    }

    public static Validation<PostalCode> of(String raw) {
        if (raw == null || raw.isBlank())
            return Validation.invalidOne("postalCode", "REQUIRED");
        return Validation.valid(new PostalCode(raw.trim()));
    }

    public String value() { return value; }

    @Override
    public String toString() { return value; }
}
