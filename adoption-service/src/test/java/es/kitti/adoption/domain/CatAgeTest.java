package es.kitti.adoption.domain;

import es.kitti.mon.error.ValidationError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class CatAgeTest {

    @Test
    void of_null_returnsRequired() {
        String expected = "REQUIRED";

        var violations = CatAge.of(null).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(expected, violations.getFirst().code());
    }

    @Test
    void of_negative_returnsInvalidRange() {
        Integer input   = -1;
        String expected = "INVALID_RANGE";

        var violations = CatAge.of(input).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(expected, violations.getFirst().code());
    }

    @Test
    void of_aboveMax_returnsInvalidRange() {
        Integer input   = 31;
        String expected = "INVALID_RANGE";

        var violations = CatAge.of(input).match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(expected, violations.getFirst().code());
    }

    @Test
    void of_zero_returnsValid() {
        var age = CatAge.of(0).match(
                err -> fail("Expected valid: " + err.violations()),
                a   -> a);

        assertEquals(0, age.value());
    }

    @Test
    void of_max_returnsValid() {
        Integer input    = 30;
        Integer expected = 30;

        var age = CatAge.of(input).match(
                err -> fail("Expected valid: " + err.violations()),
                a   -> a);

        assertEquals(expected, age.value());
    }
}
