package es.kitti.mon.either;

import es.kitti.mon.error.FieldViolation;
import es.kitti.mon.error.ValidationError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {

    @Test
    void valid_isValid() {
        assertTrue(Validation.valid("x") instanceof Validation.Valid<?>);
    }

    @Test
    void invalid_isInvalid() {
        var error = new ValidationError(List.of(new FieldViolation("f", "REQUIRED")));
        assertTrue(Validation.invalid(error) instanceof Validation.Invalid<?>);
    }

    @Test
    void invalidOne_singleViolation() {
        var v = (Validation.Invalid<?>) Validation.invalidOne("email", "INVALID_EMAIL");
        assertEquals(1, v.error().violations().size());
        assertEquals("email", v.error().violations().getFirst().field());
        assertEquals("INVALID_EMAIL", v.error().violations().getFirst().code());
    }

    @Test
    void map_onValid_transforms() {
        var result = Validation.valid(3).map(n -> n * 2);
        assertEquals(6, ((Validation.Valid<?>) result).value());
    }

    @Test
    void map_onInvalid_passesThrough() {
        var error = new ValidationError(List.of(new FieldViolation("f", "REQUIRED")));
        var result = Validation.<Integer>invalid(error).map(n -> n * 2);
        assertTrue(result instanceof Validation.Invalid<?>);
    }

    @Test
    void zip_bothValid_combines() {
        var a = Validation.valid("hello");
        var b = Validation.valid(5);
        var result = a.zip(b, (s, n) -> s + n);
        assertEquals("hello5", ((Validation.Valid<?>) result).value());
    }

    @Test
    void zip_leftInvalid_propagatesLeftErrors() {
        var a = Validation.invalidOne("name", "REQUIRED");
        var b = Validation.valid(5);
        var result = a.zip(b, (s, n) -> s);
        var invalid = (Validation.Invalid<?>) result;
        assertEquals(1, invalid.error().violations().size());
        assertEquals("name", invalid.error().violations().getFirst().field());
    }

    @Test
    void zip_rightInvalid_propagatesRightErrors() {
        var a = Validation.valid("x");
        var b = Validation.invalidOne("age", "TOO_SMALL");
        var result = a.zip(b, (s, n) -> s);
        var invalid = (Validation.Invalid<?>) result;
        assertEquals("age", invalid.error().violations().getFirst().field());
    }

    @Test
    void zip_bothInvalid_accumulatesAllErrors() {
        var a = Validation.invalidOne("name", "REQUIRED");
        var b = Validation.invalidOne("age", "TOO_SMALL");
        var result = a.zip(b, (s, n) -> s);
        var invalid = (Validation.Invalid<?>) result;
        assertEquals(2, invalid.error().violations().size());
    }

    @Test
    void required_nonNull_passesThrough() {
        var result = Validation.valid("x").required("field", "value");
        assertTrue(result instanceof Validation.Valid<?>);
    }

    @Test
    void required_null_addsRequiredViolation() {
        var result = Validation.valid("x").required("field", null);
        var invalid = (Validation.Invalid<?>) result;
        assertEquals("REQUIRED", invalid.error().violations().getFirst().code());
        assertEquals("field", invalid.error().violations().getFirst().field());
    }

    @Test
    void requiredString_valid_passesThrough() {
        assertTrue(Validation.valid("x").requiredString("f", "hello") instanceof Validation.Valid<?>);
    }

    @Test
    void requiredString_null_addsViolation() {
        var result = Validation.valid("x").requiredString("f", null);
        assertEquals("REQUIRED", ((Validation.Invalid<?>) result).error().violations().getFirst().code());
    }

    @Test
    void requiredString_blank_addsViolation() {
        var result = Validation.valid("x").requiredString("f", "   ");
        assertEquals("REQUIRED", ((Validation.Invalid<?>) result).error().violations().getFirst().code());
    }

    @Test
    void optional_null_skipsValidator() {
        var result = Validation.valid("base").optional(null, v -> Validation.invalidOne("f", "ERR"));
        assertTrue(result instanceof Validation.Valid<?>);
    }

    @Test
    void optional_nonNull_appliesValidator() {
        var result = Validation.valid("base").optional("value", v -> Validation.invalidOne("f", "ERR"));
        assertTrue(result instanceof Validation.Invalid<?>);
    }

    @Test
    void fromBusiness_onValid_returnsValue() {
        assertEquals("x", Validation.valid("x").fromBusiness());
    }

    @Test
    void fromBusiness_onInvalid_throwsIllegalStateException() {
        var v = Validation.invalidOne("f", "REQUIRED");
        assertThrows(IllegalStateException.class, v::fromBusiness);
    }

    @Test
    void toEither_onValid_returnsRight() {
        var result = Validation.valid("x").toEither();
        assertTrue(result.isRight());
        assertEquals("x", result.getOrElse(null));
    }

    @Test
    void toEither_onInvalid_returnsLeftWithValidationError() {
        var result = Validation.invalidOne("f", "REQUIRED").toEither();
        assertTrue(result.isLeft());
        assertInstanceOf(ValidationError.class, ((Either.Left<?, ?>) result).value());
    }
}
