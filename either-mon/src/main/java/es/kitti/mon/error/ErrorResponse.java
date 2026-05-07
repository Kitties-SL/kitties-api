package es.kitti.mon.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String code,
        List<FieldViolation> violations,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(DomainError error) {
        if (error instanceof ValidationError ve) {
            return new ErrorResponse(422, "VALIDATION_FAILED", ve.violations(), LocalDateTime.now());
        }
        return new ErrorResponse(error.httpStatus(), error.code(), null, LocalDateTime.now());
    }
}
