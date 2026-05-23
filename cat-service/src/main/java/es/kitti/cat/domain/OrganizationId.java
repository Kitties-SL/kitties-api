package es.kitti.cat.domain;

import es.kitti.mon.either.Validation;

public final class OrganizationId {

    private final long value;

    private OrganizationId(long value) {
        this.value = value;
    }

    public static Validation<OrganizationId> of(Long raw) {
        if (raw == null)
            return Validation.invalidOne("organizationId", "REQUIRED");
        if (raw <= 0)
            return Validation.invalidOne("organizationId", "INVALID_RANGE");
        return Validation.valid(new OrganizationId(raw));
    }

    public long value() { return value; }

    @Override
    public String toString() { return String.valueOf(value); }
}
