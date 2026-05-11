package es.kitti.user.domain;

import es.kitti.mon.error.ValidationError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailTest {

    @Test
    void of_null_returnsRequired() {
        var violations = Email.of(null).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(1,         violations.size());
        assertEquals("email",   violations.getFirst().field());
        assertEquals("REQUIRED", violations.getFirst().code());
    }

    @Test
    void of_blank_returnsRequired() {
        String input    = "   ";
        String expected = "REQUIRED";

        var violations = Email.of(input).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(expected, violations.getFirst().code());
    }

    @Test
    void of_invalidFormat_returnsInvalidEmail() {
        String input    = "notanemail";
        String expected = "INVALID_EMAIL";

        var violations = Email.of(input).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(expected, violations.getFirst().code());
    }

    @Test
    void of_valid_returnsNormalizedValue() {
        String input    = "USER@KITTI.ES";
        String expected = "user@kitti.es";

        var email = Email.of(input).match(
                err -> fail("Expected valid: " + err.violations()),
                e   -> e);

        assertEquals(expected, email.value());
    }
}
