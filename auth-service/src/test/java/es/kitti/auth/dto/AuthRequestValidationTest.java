package es.kitti.auth.dto;

import es.kitti.mon.error.ValidationError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class AuthRequestValidationTest {

    private static final String VALID_EMAIL    = "user@kitti.es";
    private static final String VALID_PASSWORD = "password123";

    @Test
    void validate_allValid_emailNormalized() {
        String input    = "USER@KITTI.ES";
        String expected = "user@kitti.es";

        var result = new AuthRequest(input, VALID_PASSWORD).validate().match(
                err -> fail("Expected valid: " + err.violations()),
                r   -> r);

        assertEquals(expected, result.email());
    }

    @Test
    void validate_invalidEmail_singleViolation() {
        String input    = "notanemail";
        String expected = "email";

        var violations = new AuthRequest(input, VALID_PASSWORD).validate().match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(1,        violations.size());
        assertEquals(expected, violations.getFirst().field());
    }

    @Test
    void validate_blankPassword_singleViolation() {
        String input    = "";
        String expected = "password";

        var violations = new AuthRequest(VALID_EMAIL, input).validate().match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(1,        violations.size());
        assertEquals(expected, violations.getFirst().field());
    }

    @Test
    void validate_invalidEmailAndPassword_accumulatesBothErrors() {
        int expected = 2;

        var violations = new AuthRequest("bad", "").validate().match(
                ValidationError::violations,
                __ -> fail("Expected invalid"));

        assertEquals(expected, violations.size());
    }
}
