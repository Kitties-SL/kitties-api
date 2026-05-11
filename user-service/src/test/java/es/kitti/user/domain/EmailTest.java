package es.kitti.user.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailTest {

    @Test
    void of_null_returnsRequired() {
        var violations = Email.of(null).match(
                err -> err.violations(),
                __ -> fail("Expected invalid"));
        assertEquals(1, violations.size());
        assertEquals("email",    violations.getFirst().field());
        assertEquals("REQUIRED", violations.getFirst().code());
    }

    @Test
    void of_blank_returnsRequired() {
        var violations = Email.of("   ").match(
                err -> err.violations(),
                __ -> fail("Expected invalid"));
        assertEquals("REQUIRED", violations.getFirst().code());
    }

    @Test
    void of_invalidFormat_returnsInvalidEmail() {
        var violations = Email.of("notanemail").match(
                err -> err.violations(),
                __ -> fail("Expected invalid"));
        assertEquals("INVALID_EMAIL", violations.getFirst().code());
    }

    @Test
    void of_valid_returnsNormalizedValue() {
        var email = Email.of("USER@KITTI.ES").match(
                err -> fail("Expected valid: " + err.violations()),
                e   -> e);
        assertEquals("user@kitti.es", email.value());
    }
}
