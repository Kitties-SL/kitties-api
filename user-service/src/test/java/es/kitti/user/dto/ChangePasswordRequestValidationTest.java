package es.kitti.user.dto;

import es.kitti.mon.error.FieldViolation;
import es.kitti.mon.error.ValidationError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChangePasswordRequestValidationTest {

    private static final String VALID_CURRENT = "oldpass123";
    private static final String VALID_NEW     = "newpass456";

    @Test
    void validate_allValid_returnsRequest() {
        var result = new ChangePasswordRequest(VALID_CURRENT, VALID_NEW)
                .validate()
                .match(err -> fail("Expected valid: " + err.violations()), r -> r);

        assertEquals(VALID_CURRENT, result.currentPassword());
        assertEquals(VALID_NEW,     result.newPassword());
    }

    @Test
    void validate_blankCurrentPassword_singleViolation() {
        var violations = new ChangePasswordRequest("", VALID_NEW)
                .validate()
                .match(ValidationError::violations, __ -> fail("Expected invalid"));

        assertEquals(1, violations.size());
        assertEquals("currentPassword", violations.getFirst().field());
        assertEquals("REQUIRED",        violations.getFirst().code());
    }

    @Test
    void validate_nullNewPassword_singleViolation() {
        var violations = new ChangePasswordRequest(VALID_CURRENT, null)
                .validate()
                .match(ValidationError::violations, __ -> fail("Expected invalid"));

        assertEquals(1, violations.size());
        assertEquals("newPassword", violations.getFirst().field());
        assertEquals("REQUIRED",    violations.getFirst().code());
    }

    @Test
    void validate_shortNewPassword_returnsInvalidSize() {
        var violations = new ChangePasswordRequest(VALID_CURRENT, "short")
                .validate()
                .match(ValidationError::violations, __ -> fail("Expected invalid"));

        assertEquals(1, violations.size());
        assertEquals("newPassword",   violations.getFirst().field());
        assertEquals("INVALID_SIZE",  violations.getFirst().code());
    }

    @Test
    void validate_bothInvalid_accumulatesBothErrors() {
        List<FieldViolation> violations = new ChangePasswordRequest(null, "x")
                .validate()
                .match(ValidationError::violations, __ -> fail("Expected invalid"));

        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.field().equals("currentPassword") && v.code().equals("REQUIRED")));
        assertTrue(violations.stream().anyMatch(v -> v.field().equals("newPassword")     && v.code().equals("INVALID_SIZE")));
    }
}