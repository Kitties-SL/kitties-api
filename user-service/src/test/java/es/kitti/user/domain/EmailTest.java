package es.kitti.user.domain;

import es.kitti.mon.either.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailTest {

    @Test
    void of_null_returnsInvalidRequired() {
        var result = Email.of(null);
        assertInvalidWithCode(result, "email", "REQUIRED");
    }

    @Test
    void of_blank_returnsInvalidRequired() {
        var result = Email.of("   ");
        assertInvalidWithCode(result, "email", "REQUIRED");
    }

    @Test
    void of_invalidFormat_returnsInvalidEmail() {
        var result = Email.of("notanemail");
        assertInvalidWithCode(result, "email", "INVALID_EMAIL");
    }

    @Test
    void of_valid_returnsValid() {
        var result = Email.of("user@kitti.es");
        assertTrue(result instanceof Validation.Valid<?>);
        assertEquals("user@kitti.es", ((Validation.Valid<Email>) result).value().value());
    }

    @Test
    void of_uppercaseEmail_normalizesToLowercase() {
        var result = Email.of("USER@KITTI.ES");
        assertTrue(result instanceof Validation.Valid<?>);
        assertEquals("user@kitti.es", ((Validation.Valid<Email>) result).value().value());
    }

    private void assertInvalidWithCode(Validation<Email> result, String field, String code) {
        assertTrue(result instanceof Validation.Invalid<?>);
        var violations = ((Validation.Invalid<Email>) result).error().violations();
        assertEquals(1, violations.size());
        assertEquals(field, violations.getFirst().field());
        assertEquals(code, violations.getFirst().code());
    }
}
