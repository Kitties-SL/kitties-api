package es.kitti.adoption.intake.dto;

import es.kitti.mon.error.ValidationError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class IntakeRequestCreateRequestValidationTest {

    private static IntakeRequestCreateRequest valid() {
        return new IntakeRequestCreateRequest(200L, "Mishi", 3, "Santa Cruz de Tenerife", "La Orotava", true, null);
    }

    @Test
    void validate_allValid_returnsValid() {
        var result = valid().validate().match(
                err -> fail("Expected valid: " + err.violations()),
                r   -> r);

        assertEquals("Mishi", result.catName());
    }

    @Test
    void validate_missingOrganizationId_returnsRequired() {
        String expected = "targetOrganizationId";

        var violations = new IntakeRequestCreateRequest(null, "Mishi", 3, "SC Tenerife", "Orotava", true, null)
                .validate().match(ValidationError::violations, __ -> fail("Expected invalid"));

        assertEquals(1,        violations.size());
        assertEquals(expected, violations.getFirst().field());
    }

    @Test
    void validate_invalidCatAge_returnsInvalidRange() {
        String expected = "INVALID_RANGE";

        var violations = new IntakeRequestCreateRequest(200L, "Mishi", -1, "SC Tenerife", "Orotava", true, null)
                .validate().match(ValidationError::violations, __ -> fail("Expected invalid"));

        assertEquals(expected, violations.getFirst().code());
    }

    @Test
    void validate_blankCatName_returnsRequired() {
        var violations = new IntakeRequestCreateRequest(200L, "", 3, "SC Tenerife", "Orotava", true, null)
                .validate().match(ValidationError::violations, __ -> fail("Expected invalid"));

        assertEquals("catName", violations.getFirst().field());
    }

    @Test
    void validate_multipleErrors_allAccumulated() {
        int expected = 6;

        var violations = new IntakeRequestCreateRequest(null, "", -1, "", "", null, null)
                .validate().match(ValidationError::violations, __ -> fail("Expected invalid"));

        assertEquals(expected, violations.size());
    }
}
