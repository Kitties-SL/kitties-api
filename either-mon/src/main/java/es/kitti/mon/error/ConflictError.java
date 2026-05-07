package es.kitti.mon.error;

public record ConflictError(String code) implements DomainError {
    @Override public int httpStatus() { return 409; }
}
