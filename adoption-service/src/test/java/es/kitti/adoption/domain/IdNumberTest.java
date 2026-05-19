package es.kitti.adoption.domain;

import es.kitti.mon.error.ValidationError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class IdNumberTest {

    // DNI: 12345678 % 23 = 14 → Z  (ejemplo del Real Decreto 255/2025)
    // NIE: Y1234567 → 11234567 % 23 = 10 → X

    @Test
    void of_null_returnsRequired() {
        String expected = "REQUIRED";

        var violations = IdNumber.of(null).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(expected, violations.getFirst().code());
    }

    @Test
    void of_blank_returnsRequired() {
        String input    = "   ";
        String expected = "REQUIRED";

        var violations = IdNumber.of(input).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(expected, violations.getFirst().code());
    }

    @Test
    void of_randomString_returnsInvalidFormat() {
        String input    = "NOTANID";
        String expected = "INVALID_FORMAT";

        var violations = IdNumber.of(input).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(expected, violations.getFirst().code());
    }

    @Test
    void of_validDni_returnsValid() {
        String input    = "12345678Z";
        String expected = "12345678Z";

        var idNumber = IdNumber.of(input).match(
                err -> fail("Expected valid: " + err.violations()),
                id  -> id);

        assertEquals(expected, idNumber.value());
    }

    @Test
    void of_dniLowercase_normalizesAndReturnsValid() {
        String input    = "12345678z";
        String expected = "12345678Z";

        var idNumber = IdNumber.of(input).match(
                err -> fail("Expected valid: " + err.violations()),
                id  -> id);

        assertEquals(expected, idNumber.value());
    }

    @Test
    void of_dniWrongControlDigit_returnsInvalidControlDigit() {
        String input    = "12345678A";
        String expected = "INVALID_CONTROL_DIGIT";

        var violations = IdNumber.of(input).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(expected, violations.getFirst().code());
    }

    @Test
    void of_validNie_returnsValid() {
        String input    = "Y1234567X";
        String expected = "Y1234567X";

        var idNumber = IdNumber.of(input).match(
                err -> fail("Expected valid: " + err.violations()),
                id  -> id);

        assertEquals(expected, idNumber.value());
    }

    @Test
    void of_nieWrongControlDigit_returnsInvalidControlDigit() {
        String input    = "Y1234567A";
        String expected = "INVALID_CONTROL_DIGIT";

        var violations = IdNumber.of(input).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(expected, violations.getFirst().code());
    }
}
