package es.kitti.mon.error;

public record NotFoundError(String code) implements DomainError {
    @Override public int httpStatus() { return 404; }
}
