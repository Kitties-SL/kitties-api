package es.kitti.mon.error;

public interface DomainError {
    int httpStatus();
    String code();
}
