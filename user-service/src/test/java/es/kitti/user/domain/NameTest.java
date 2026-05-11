package es.kitti.user.domain;

import es.kitti.mon.either.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NameTest {

    @Test
    void of_null_returnsInvalidRequired() {
        var result = Name.of("name", null);
        assertInvalidWithCode(result, "name", "REQUIRED");
    }

    @Test
    void of_blank_returnsInvalidRequired() {
        var result = Name.of("surname", "   ");
        assertInvalidWithCode(result, "surname", "REQUIRED");
    }

    @Test
    void of_tooLong_returnsInvalidSize() {
        var result = Name.of("name", "a".repeat(101));
        assertInvalidWithCode(result, "name", "INVALID_SIZE");
    }

    @Test
    void of_valid_returnsValid() {
        var result = Name.of("name", "Francisco");
        assertTrue(result instanceof Validation.Valid<?>);
        assertEquals("Francisco", ((Validation.Valid<Name>) result).value().value());
    }

    @Test
    void of_withWhitespace_trimsValue() {
        var result = Name.of("name", "  Francisco  ");
        assertTrue(result instanceof Validation.Valid<?>);
        assertEquals("Francisco", ((Validation.Valid<Name>) result).value().value());
    }

    @Test
    void of_propagatesFieldName() {
        var result = Name.of("surname", null);
        assertInvalidWithCode(result, "surname", "REQUIRED");
    }

    private void assertInvalidWithCode(Validation<Name> result, String field, String code) {
        assertTrue(result instanceof Validation.Invalid<?>);
        var violations = ((Validation.Invalid<Name>) result).error().violations();
        assertEquals(1, violations.size());
        assertEquals(field, violations.getFirst().field());
        assertEquals(code, violations.getFirst().code());
    }
}
