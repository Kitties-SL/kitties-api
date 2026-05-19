package es.kitti.user.domain;

import es.kitti.mon.error.ValidationError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class PasswordTest {

    @Test
    void of_null_returnsRequired() {
        var violations = Password.of(null).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals("REQUIRED", violations.getFirst().code());
    }

    @Test
    void of_blank_returnsRequired() {
        String input    = "   ";
        String expected = "REQUIRED";

        var violations = Password.of(input).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(expected, violations.getFirst().code());
    }

    @Test
    void of_tooShort_returnsInvalidSize() {
        String input    = "short";
        String expected = "INVALID_SIZE";

        var violations = Password.of(input).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(expected, violations.getFirst().code());
    }

    @Test
    void of_exactMinLength_returnsValid() {
        String input    = "12345678";
        String expected = "12345678";

        var password = Password.of(input).match(
                err -> fail("Expected valid: " + err.violations()),
                p   -> p);

        assertEquals(expected, password.value());
    }
}
