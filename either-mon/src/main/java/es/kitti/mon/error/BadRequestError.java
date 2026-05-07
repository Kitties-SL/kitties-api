package es.kitti.mon.error;

public record BadRequestError(String code) implements DomainError {
    @Override public int httpStatus() { return 400; }
}