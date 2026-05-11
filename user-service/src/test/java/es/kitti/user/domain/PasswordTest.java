package es.kitti.user.domain;

import es.kitti.mon.either.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordTest {

    @Test
    void of_null_returnsInvalidRequired() {
        var result = Password.of(null);
        assertInvalidWithCode(result, "password", "REQUIRED");
    }

    @Test
    void of_blank_returnsInvalidRequired() {
        var result = Password.of("   ");
        assertInvalidWithCode(result, "password", "REQUIRED");
    }

    @Test
    void of_tooShort_returnsInvalidSize() {
        var result = Password.of("short");
        assertInvalidWithCode(result, "password", "INVALID_SIZE");
    }

    @Test
    void of_exactMinLength_returnsValid() {
        var result = Password.of("12345678");
        assertTrue(result instanceof Validation.Valid<?>);
    }

    @Test
    void of_valid_returnsValid() {
        var result = Password.of("securepassword");
        assertTrue(result instanceof Validation.Valid<?>);
        assertEquals("securepassword", ((Validation.Valid<Password>) result).value().value());
    }

    private void assertInvalidWithCode(Validation<Password> result, String field, String code) {
        assertTrue(result instanceof Validation.Invalid<?>);
        var violations = ((Validation.Invalid<Password>) result).error().violations();
        assertEquals(1, violations.size());
        assertEquals(field, violations.getFirst().field());
        assertEquals(code, violations.getFirst().code());
    }
}
