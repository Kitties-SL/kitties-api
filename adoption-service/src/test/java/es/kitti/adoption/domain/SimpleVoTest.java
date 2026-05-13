package es.kitti.adoption.domain;

import es.kitti.mon.error.ValidationError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleVoTest {

    // --- Phone ---

    @Test
    void phone_null_returnsRequired() {
        var violations = Phone.of(null).match(ValidationError::violations, __ -> fail("Expected invalid"));
        assertEquals("phone",    violations.getFirst().field());
        assertEquals("REQUIRED", violations.getFirst().code());
    }

    @Test
    void phone_valid_returnsValue() {
        var phone = Phone.of("+34 600 000 000").match(err -> fail("Expected valid: " + err.violations()), p -> p);
        assertEquals("+34 600 000 000", phone.value());
    }

    // --- PostalCode ---

    @Test
    void postalCode_null_returnsRequired() {
        var violations = PostalCode.of(null).match(ValidationError::violations, __ -> fail("Expected invalid"));
        assertEquals("postalCode", violations.getFirst().field());
        assertEquals("REQUIRED",   violations.getFirst().code());
    }

    @Test
    void postalCode_valid_returnsValue() {
        var pc = PostalCode.of("38300").match(err -> fail("Expected valid: " + err.violations()), p -> p);
        assertEquals("38300", pc.value());
    }

    // --- Address ---

    @Test
    void address_blank_returnsRequired() {
        var violations = Address.of("  ").match(ValidationError::violations, __ -> fail("Expected invalid"));
        assertEquals("address",  violations.getFirst().field());
        assertEquals("REQUIRED", violations.getFirst().code());
    }

    @Test
    void address_valid_trimsValue() {
        var addr = Address.of("  Calle Mayor 1  ").match(err -> fail("Expected valid: " + err.violations()), a -> a);
        assertEquals("Calle Mayor 1", addr.value());
    }

    // --- City ---

    @Test
    void city_blank_returnsRequired() {
        var violations = City.of("").match(ValidationError::violations, __ -> fail("Expected invalid"));
        assertEquals("city",     violations.getFirst().field());
        assertEquals("REQUIRED", violations.getFirst().code());
    }

    @Test
    void city_valid_trimsValue() {
        var city = City.of("  La Orotava  ").match(err -> fail("Expected valid: " + err.violations()), c -> c);
        assertEquals("La Orotava", city.value());
    }

    // --- Name ---

    @Test
    void name_blank_returnsRequired() {
        var violations = Name.of("fullName", "").match(ValidationError::violations, __ -> fail("Expected invalid"));
        assertEquals("fullName", violations.getFirst().field());
        assertEquals("REQUIRED", violations.getFirst().code());
    }

    @Test
    void name_tooLong_returnsInvalidSize() {
        var violations = Name.of("fullName", "a".repeat(101)).match(ValidationError::violations, __ -> fail("Expected invalid"));
        assertEquals("INVALID_SIZE", violations.getFirst().code());
    }

    @Test
    void name_valid_trimsValue() {
        var name = Name.of("fullName", "  Francisco García  ").match(err -> fail("Expected valid: " + err.violations()), n -> n);
        assertEquals("Francisco García", name.value());
    }
}
