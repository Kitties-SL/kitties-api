package es.kitti.adoption.dto;

import es.kitti.mon.error.ValidationError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdoptionFormCreateRequestValidationTest {

    private static AdoptionFormCreateRequest valid() {
        return new AdoptionFormCreateRequest(
                "Francisco García", "12345678Z", "+34 600 000 000",
                "Calle Mayor 1", "La Orotava", "38300",
                true, true, true, true, true, null);
    }

    @Test
    void validate_allValid_returnsValid() {
        var result = valid().validate().match(
                err -> fail("Expected valid: " + err.violations()),
                r   -> r);

        assertEquals("Francisco García", result.fullName());
    }

    @Test
    void validate_invalidIdNumber_returnsInvalidControlDigit() {
        String expected = "INVALID_CONTROL_DIGIT";

        var violations = new AdoptionFormCreateRequest(
                "Francisco García", "12345678A", "+34 600 000 000",
                "Calle Mayor 1", "La Orotava", "38300",
                true, true, true, true, true, null)
                .validate().match(ValidationError::violations, __ -> fail("Expected invalid"));

        assertEquals(expected, violations.getFirst().code());
    }

    @Test
    void validate_missingConsents_accumulatesErrors() {
        int expected = 5;

        var violations = new AdoptionFormCreateRequest(
                "Francisco García", "12345678Z", "+34 600 000 000",
                "Calle Mayor 1", "La Orotava", "38300",
                null, null, null, null, null, null)
                .validate().match(ValidationError::violations, __ -> fail("Expected invalid"));

        assertEquals(expected, violations.size());
    }

    @Test
    void validate_blankFullNameAndInvalidId_accumulatesBothErrors() {
        int expected = 2;

        var violations = new AdoptionFormCreateRequest(
                "", "NOTANID", "+34 600 000 000",
                "Calle Mayor 1", "La Orotava", "38300",
                true, true, true, true, true, null)
                .validate().match(ValidationError::violations, __ -> fail("Expected invalid"));

        assertEquals(expected, violations.size());
    }
}
