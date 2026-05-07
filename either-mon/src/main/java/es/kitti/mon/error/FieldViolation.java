package es.kitti.mon.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

public record FieldViolation(
        String field,
        String code,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> params
) {
    public FieldViolation(String field, String code) {
        this(field, code, Map.of());
    }
}
