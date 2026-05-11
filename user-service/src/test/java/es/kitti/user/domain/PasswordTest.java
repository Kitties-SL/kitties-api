package es.kitti.user.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordTest {

    @Test
    void of_null_returnsRequired() {
        var violations = Password.of(null).match(
                err -> err.violations(),
                __ -> fail("Expected invalid"));
        assertEquals("REQUIRED", violations.getFirst().code());
    }

    @Test
    void of_blank_returnsRequired() {
        var violations = Password.of("   ").match(
                err -> err.violations(),
                __ -> fail("Expected invalid"));
        assertEquals("REQUIRED", violations.getFirst().code());
    }

    @Test
    void of_tooShort_returnsInvalidSize() {
        var violations = Password.of("short").match(
                err -> err.violations(),
                __ -> fail("Expected invalid"));
        assertEquals("INVALID_SIZE", violations.getFirst().code());
    }

    @Test
    void of_exactMinLength_returnsValid() {
        var password = Password.of("12345678").match(
                err -> fail("Expected valid: " + err.violations()),
                p   -> p);
        assertEquals("12345678", password.value());
    }
}
