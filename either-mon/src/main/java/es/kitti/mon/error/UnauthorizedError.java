package es.kitti.mon.error;

public record UnauthorizedError(String code) implements DomainError {
    @Override public int httpStatus() { return 401; }
}
