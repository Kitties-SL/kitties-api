package es.kitti.mon.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.*;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConstraintViolationMapperTest {

    @Test
    void notNull_mapsToRequired() {
        var result = ConstraintViolationMapper.toValidationError(Set.of(violation("name", NotNull.class, Map.of())));
        assertEquals("REQUIRED", result.violations().getFirst().code());
    }

    @Test
    void notBlank_mapsToRequired() {
        var result = ConstraintViolationMapper.toValidationError(Set.of(violation("email", NotBlank.class, Map.of())));
        assertEquals("REQUIRED", result.violations().getFirst().code());
    }

    @Test
    void size_mapsToInvalidSize() {
        var result = ConstraintViolationMapper.toValidationError(Set.of(
                violation("name", Size.class, Map.of("min", 2, "max", 50))));
        assertEquals("INVALID_SIZE", result.violations().getFirst().code());
    }

    @Test
    void min_mapsToTooSmall() {
        var result = ConstraintViolationMapper.toValidationError(Set.of(
                violation("age", Min.class, Map.of("value", 1L))));
        assertEquals("TOO_SMALL", result.violations().getFirst().code());
    }

    @Test
    void max_mapsToTooLarge() {
        var result = ConstraintViolationMapper.toValidationError(Set.of(
                violation("age", Max.class, Map.of("value", 120L))));
        assertEquals("TOO_LARGE", result.violations().getFirst().code());
    }

    @Test
    void email_mapsToInvalidEmail() {
        var result = ConstraintViolationMapper.toValidationError(Set.of(violation("email", Email.class, Map.of())));
        assertEquals("INVALID_EMAIL", result.violations().getFirst().code());
    }

    @Test
    void pattern_mapsToInvalidFormat() {
        var result = ConstraintViolationMapper.toValidationError(Set.of(
                violation("code", Pattern.class, Map.of("regexp", "[A-Z]+"))));
        assertEquals("INVALID_FORMAT", result.violations().getFirst().code());
    }

    @Test
    void fieldExtractedFromPropertyPath() {
        var v = violation("address.city", NotNull.class, Map.of());
        var result = ConstraintViolationMapper.toValidationError(Set.of(v));
        assertEquals("city", result.violations().getFirst().field());
    }

    @Test
    void params_significantValuesIncluded() {
        var result = ConstraintViolationMapper.toValidationError(Set.of(
                violation("name", Size.class, Map.of("min", 5, "max", Integer.MAX_VALUE))));
        var params = result.violations().getFirst().params();
        assertTrue(params.containsKey("min"));
        assertEquals(5, params.get("min"));
    }

    @Test
    void params_maxIntExcluded() {
        var result = ConstraintViolationMapper.toValidationError(Set.of(
                violation("name", Size.class, Map.of("min", 5, "max", Integer.MAX_VALUE))));
        assertFalse(result.violations().getFirst().params().containsKey("max"));
    }

    @Test
    void multipleViolations_allMapped() {
        var result = ConstraintViolationMapper.toValidationError(Set.of(
                violation("name", NotNull.class, Map.of()),
                violation("email", Email.class, Map.of())));
        assertEquals(2, result.violations().size());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <A extends Annotation> ConstraintViolation<?> violation(
            String path, Class<A> annotationType, Map<String, Object> attrs) {

        ConstraintViolation cv       = mock(ConstraintViolation.class);
        ConstraintDescriptor descriptor = mock(ConstraintDescriptor.class);
        Annotation annotation        = mock(annotationType);

        when(cv.getPropertyPath()).thenReturn(new SimplePropertyPath(path));
        when(cv.getConstraintDescriptor()).thenReturn(descriptor);
        when(descriptor.getAnnotation()).thenReturn(annotation);
        when(annotation.annotationType()).thenReturn((Class) annotationType);

        Map<String, Object> allAttrs = new java.util.HashMap<>(attrs);
        allAttrs.put("groups", new Class[0]);
        allAttrs.put("message", "");
        allAttrs.put("payload", new Class[0]);
        when(descriptor.getAttributes()).thenReturn(allAttrs);

        return cv;
    }

    private record SimplePropertyPath(String path) implements jakarta.validation.Path {
        @Override public java.util.Iterator<Node> iterator() { return java.util.Collections.emptyIterator(); }
        @Override public String toString() { return path; }
    }
}
