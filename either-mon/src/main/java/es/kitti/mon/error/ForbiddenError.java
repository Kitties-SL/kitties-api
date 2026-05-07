package es.kitti.mon.error;

public record ForbiddenError(String code) implements DomainError {
    @Override public int httpStatus() { return 403; }
}
