package es.kitti.user.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NameTest {

    @Test
    void of_null_returnsRequired() {
        var violations = Name.of("name", null).match(
                err -> err.violations(),
                __ -> fail("Expected invalid"));
        assertEquals("name",     violations.getFirst().field());
        assertEquals("REQUIRED", violations.getFirst().code());
    }

    @Test
    void of_blank_returnsRequired() {
        var violations = Name.of("surname", "   ").match(
                err -> err.violations(),
                __ -> fail("Expected invalid"));
        assertEquals("surname",  violations.getFirst().field());
        assertEquals("REQUIRED", violations.getFirst().code());
    }

    @Test
    void of_tooLong_returnsInvalidSize() {
        var violations = Name.of("name", "a".repeat(101)).match(
                err -> err.violations(),
                __ -> fail("Expected invalid"));
        assertEquals("INVALID_SIZE", violations.getFirst().code());
    }

    @Test
    void of_valid_trimmsAndReturnsValue() {
        var name = Name.of("name", "  Francisco  ").match(
                err -> fail("Expected valid: " + err.violations()),
                n   -> n);
        assertEquals("Francisco", name.value());
    }
}
