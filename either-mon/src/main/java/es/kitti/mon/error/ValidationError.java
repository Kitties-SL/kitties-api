package es.kitti.mon.error;

import java.util.List;

public record ValidationError(List<FieldViolation> violations) implements DomainError {
    @Override public int httpStatus() { return 422; }
    @Override public String code()    { return "VALIDATION_FAILED"; }
}
