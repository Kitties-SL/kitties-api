package es.kitti.user.domain;

import es.kitti.mon.error.ValidationError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class NameTest {

    @Test
    void of_null_returnsRequired() {
        String field    = "name";
        String expected = "REQUIRED";

        var violations = Name.of(field, null).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(field,    violations.getFirst().field());
        assertEquals(expected, violations.getFirst().code());
    }

    @Test
    void of_blank_returnsRequired() {
        String field    = "surname";
        String expected = "REQUIRED";

        var violations = Name.of(field, "   ").match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(field,    violations.getFirst().field());
        assertEquals(expected, violations.getFirst().code());
    }

    @Test
    void of_tooLong_returnsInvalidSize() {
        String input    = "a".repeat(101);
        String expected = "INVALID_SIZE";

        var violations = Name.of("name", input).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(expected, violations.getFirst().code());
    }

    @Test
    void of_valid_trimsAndReturnsValue() {
        String input    = "  Francisco  ";
        String expected = "Francisco";

        var name = Name.of("name", input).match(
                err -> fail("Expected valid: " + err.violations()),
                n   -> n);

        assertEquals(expected, name.value());
    }
}
