package es.kitti.mon.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @JsonProperty("status")     int status,
        @JsonProperty("code")       String code,
        @JsonProperty("violations") List<FieldViolation> violations,
        @JsonProperty("timestamp")  LocalDateTime timestamp
) {
    public static ErrorResponse of(DomainError error) {
        if (error instanceof ValidationError ve) {
            return new ErrorResponse(422, "VALIDATION_FAILED", ve.violations(), LocalDateTime.now());
        }
        return new ErrorResponse(error.httpStatus(), error.code(), null, LocalDateTime.now());
    }

    public static ErrorResponse internalError() {
        return new ErrorResponse(500, "INTERNAL_SERVER_ERROR", null, LocalDateTime.now());
    }
}
