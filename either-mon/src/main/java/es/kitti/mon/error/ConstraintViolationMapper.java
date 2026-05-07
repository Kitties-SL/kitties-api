package es.kitti.mon.error;

import jakarta.validation.ConstraintViolation;

import java.util.*;
import java.util.stream.Collectors;

public final class ConstraintViolationMapper {

    private static final Set<String> META_KEYS = Set.of("groups", "message", "payload");

    private ConstraintViolationMapper() {}

    public static ValidationError toValidationError(Set<? extends ConstraintViolation<?>> violations) {
        List<FieldViolation> fieldViolations = violations.stream()
                .map(ConstraintViolationMapper::toFieldViolation)
                .collect(Collectors.toList());
        return new ValidationError(fieldViolations);
    }

    private static FieldViolation toFieldViolation(ConstraintViolation<?> v) {
        String field = extractField(v.getPropertyPath().toString());
        String constraintType = v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
        String code = normalizeCode(constraintType);
        Map<String, Object> params = extractParams(v);
        return new FieldViolation(field, code, params);
    }

    private static String extractField(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
    }

    private static String normalizeCode(String constraintType) {
        return switch (constraintType.toUpperCase()) {
            case "NOTBLANK", "NOTNULL", "NOTEMPTY" -> "REQUIRED";
            case "SIZE"                             -> "INVALID_SIZE";
            case "MIN"                              -> "TOO_SMALL";
            case "MAX"                              -> "TOO_LARGE";
            case "EMAIL"                            -> "INVALID_EMAIL";
            case "PATTERN"                          -> "INVALID_FORMAT";
            case "POSITIVE"                         -> "MUST_BE_POSITIVE";
            case "POSITIVEORZERO"                   -> "MUST_BE_POSITIVE_OR_ZERO";
            case "NEGATIVE"                         -> "MUST_BE_NEGATIVE";
            case "FUTURE"                           -> "MUST_BE_FUTURE";
            case "PAST"                             -> "MUST_BE_PAST";
            default                                 -> constraintType.toUpperCase();
        };
    }

    // Strips Jakarta meta-attributes (groups, message, payload) and filters out
    // default boundary values that carry no information (Integer.MAX_VALUE, Long.MIN_VALUE…).
    private static Map<String, Object> extractParams(ConstraintViolation<?> v) {
        Map<String, Object> attrs = v.getConstraintDescriptor().getAttributes();
        Map<String, Object> params = new LinkedHashMap<>();
        attrs.entrySet().stream()
                .filter(e -> !META_KEYS.contains(e.getKey()))
                .filter(e -> isSignificantValue(e.getValue()))
                .forEach(e -> params.put(e.getKey(), e.getValue()));
        return params.isEmpty() ? Map.of() : Collections.unmodifiableMap(params);
    }

    private static boolean isSignificantValue(Object value) {
        if (value instanceof Integer i) return i != Integer.MIN_VALUE && i != Integer.MAX_VALUE;
        if (value instanceof Long l)    return l != Long.MIN_VALUE    && l != Long.MAX_VALUE;
        if (value instanceof String s)  return !s.isEmpty();
        if (value instanceof Object[] a) return a.length > 0;
        return true;
    }
}
