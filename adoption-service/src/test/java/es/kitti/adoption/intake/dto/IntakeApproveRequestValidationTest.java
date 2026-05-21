package es.kitti.adoption.intake.dto;

import es.kitti.mon.error.ValidationError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class IntakeApproveRequestValidationTest {

    private static IntakeApproveRequest valid() {
        return new IntakeApproveRequest("Female", "ES", null, null, null, null, null, true);
    }

    @Test
    void validate_allValid_returnsValid() {
        var result = valid().validate().match(
                err -> fail("Expected valid: " + err.violations()),
                r   -> r);

        assertEquals("Female", result.sex());
        assertEquals("ES", result.country());
    }

    @Test
    void validate_nullSex_returnsRequired() {
        var violations = new IntakeApproveRequest(null, "ES", null, null, null, null, null, null)
                .validate().match(ValidationError::violations, __ -> fail("Expected invalid"));

        assertTrue(violations.stream().anyMatch(v -> v.field().equals("sex") && v.code().equals("REQUIRED")));
    }

    @Test
    void validate_blankSex_returnsRequired() {
        var violations = new IntakeApproveRequest("", "ES", null, null, null, null, null, null)
                .validate().match(ValidationError::violations, __ -> fail("Expected invalid"));

        assertTrue(violations.stream().anyMatch(v -> v.field().equals("sex") && v.code().equals("REQUIRED")));
    }

    @Test
    void validate_invalidSex_returnsInvalidValue() {
        var violations = new IntakeApproveRequest("Other", "ES", null, null, null, null, null, null)
                .validate().match(ValidationError::violations, __ -> fail("Expected invalid"));

        assertTrue(violations.stream().anyMatch(v -> v.field().equals("sex") && v.code().equals("INVALID_VALUE")));
    }

    @Test
    void validate_nullCountry_returnsRequired() {
        var violations = new IntakeApproveRequest("Female", null, null, null, null, null, null, null)
                .validate().match(ValidationError::violations, __ -> fail("Expected invalid"));

        assertTrue(violations.stream().anyMatch(v -> v.field().equals("country") && v.code().equals("REQUIRED")));
    }

    @Test
    void validate_bothMissing_accumulatesViolations() {
        var violations = new IntakeApproveRequest(null, null, null, null, null, null, null, null)
                .validate().match(ValidationError::violations, __ -> fail("Expected invalid"));

        assertEquals(2, violations.size());
    }
}
