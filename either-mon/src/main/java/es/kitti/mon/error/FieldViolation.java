package es.kitti.mon.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record FieldViolation(
        @JsonProperty("field") String field,
        @JsonProperty("code")  String code,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) @JsonProperty("params") Map<String, Object> params
) {
    public FieldViolation(String field, String code) {
        this(field, code, Map.of());
    }
}
